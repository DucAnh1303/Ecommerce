package web.com.auth.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import web.com.auth.config.jwt.JwtAuthentication;
import web.com.auth.dto.UserDTO;
import web.com.auth.entity.RoleEntity;
import web.com.auth.entity.RoleEnum;
import web.com.auth.entity.UserEntity;
import web.com.auth.exception.ApiErrorCode;
import web.com.auth.exception.ApiException;
import web.com.auth.repository.RoleRepository;
import web.com.auth.repository.UserRepository;
import web.com.auth.request.AuthRequest;
import web.com.auth.response.AuthResponse;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Log4j2
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final JwtAuthentication jwtService;
    private final RefreshTokenService refreshService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public AuthResponse login(AuthRequest request) {
        Optional<UserEntity> findByUser = userRepository.findByUserName(request.getUserName());
        UserDTO  getUser = getUser(request.getUserName());
        if (findByUser.isEmpty()) {
            throw new ApiException(ApiErrorCode.USER_NOT_FOUND);
        }
        validPassword(request.getPassword(), findByUser.get().getPassword());

        String access = jwtService.generateAccessToken(getUser);
        String refresh = jwtService.generateRefreshToken(getUser);

        // 1. Thu hồi các refresh token cũ của user trong DB
        refreshService.revokeAllByUsername(getUser.getUserName());

        // 2. Thu hồi access token cũ của user (đưa vào Redis Blacklist) và lưu token mới
        String newJti = jwtService.parse(access).getId();
        tokenBlacklistService.registerActiveToken(getUser.getUserName(), newJti, Duration.ofMillis(jwtService.getAccessExp()));

        Date expiry = jwtService.parse(refresh).getExpiration();
        refreshService.save(refresh, getUser.getUserName(), expiry);
        log.info("Login account: {} Success – old active session revoked", getUser.getUserName());
        return new AuthResponse(access, refresh);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        if (!jwtService.isRefreshToken(refreshToken) || !refreshService.isValid(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH_TOKEN);
        }

        String username = jwtService.getUsername(refreshToken);
        UserDTO getUser = getUser(username);
        String newAccess = jwtService.generateAccessToken(getUser);
        String newRefresh = jwtService.generateRefreshToken(getUser);

        // Thu hồi refresh token cũ
        refreshService.revoke(refreshToken);

        // Đăng ký active token mới (đưa token cũ vào blacklist)
        String newJti = jwtService.parse(newAccess).getId();
        tokenBlacklistService.registerActiveToken(username, newJti, Duration.ofMillis(jwtService.getAccessExp()));

        Date expiry = jwtService.parse(newRefresh).getExpiration();
        refreshService.save(newRefresh, username, expiry);

        return new AuthResponse(newAccess, newRefresh);
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            tokenBlacklistService.blacklist(accessToken);
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshService.revoke(refreshToken);
        }
        log.info("Logout success — access token blacklisted, refresh token revoked");
    }

    public UserDTO validateToken(String token) {
        if (!jwtService.validateToken(token)) {
            throw new ApiException(ApiErrorCode.INVALID_TOKEN);
        }
        if (tokenBlacklistService.isBlacklisted(token)) {
            throw new ApiException(ApiErrorCode.TOKEN_BLACKLISTED);
        }
        String username = jwtService.getUsername(token);
        return getUser(username);
    }


    private UserDTO getUser(String username) {
        Optional<UserEntity> findByUser = userRepository.findByUserName(username);
        if (findByUser.isEmpty()) {
            throw new ApiException(ApiErrorCode.USER_NOT_FOUND);
        }
        List<RoleEnum> roles = roleRepository.findByUserId(findByUser.get().getId()).stream().map(RoleEntity::getRoleEnum).toList();
        return UserDTO.from(findByUser.get(), roles);
    }

    private void validPassword(String password, String passwordRaw) {
        if (!passwordEncoder.matches(password, passwordRaw)) {
            throw new ApiException(ApiErrorCode.INVALID_PASSWORD);
        }
    }
}
