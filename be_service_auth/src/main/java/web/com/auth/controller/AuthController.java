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
     * @param request             Body chứa refreshToken (tuỳ chọn)
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody(required = false) RefreshTokenRequest request) {

        String accessToken = (authorizationHeader != null && authorizationHeader.toLowerCase().startsWith("bearer "))
                ? authorizationHeader.substring(7).trim()
                : null;
        String refreshToken = request != null ? request.getRefreshToken() : null;

        authService.logout(accessToken, refreshToken);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/validate")
    public ResponseEntity<UserDTO> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.toLowerCase().startsWith("bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authorizationHeader.substring(7).trim();
        return ResponseEntity.ok(authService.validateToken(token));
    }

}
