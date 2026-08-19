package web.com.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import web.com.auth.request.AuthRequest;
import web.com.auth.request.RefreshTokenRequest;
import web.com.auth.response.AuthResponse;
import web.com.auth.service.AuthService;
import web.com.auth.dto.UserDTO;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid AuthRequest request) {
        return ResponseEntity.ok().body(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok().body(authService.refresh(request.getRefreshToken()));
    }

    /**
     * Logout: thu hồi cả access token (blacklist Redis) và refresh token (DB).
     *
     * @param authorizationHeader Header "Authorization: Bearer <accessToken>"
     * @param request             Body chứa refreshToken
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody RefreshTokenRequest request) {

        String accessToken = authorizationHeader.substring(7).trim();
        authService.logout(accessToken, request.getRefreshToken());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/validate")
    public ResponseEntity<UserDTO> validateToken(
            @RequestHeader("Authorization") String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authorizationHeader.substring(7).trim();
        return ResponseEntity.ok(authService.validateToken(token));
    }

}
