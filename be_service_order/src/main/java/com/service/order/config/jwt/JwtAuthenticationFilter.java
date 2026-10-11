package com.service.order.config.jwt;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Xác thực JWT RS256 tại chỗ bằng public key (cache từ JWKS của AUTH-SERVICE).
 * Không còn gọi Feign /api/v1/auth/validate cho mỗi request → không nghẽn ở AUTH-SERVICE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtTokenVerifier tokenVerifier;
    private final TokenBlacklistChecker blacklistChecker;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER)) {
            filterChain.doFilter(UserHeaderRequestWrapper.anonymous(request), response);
            return;
        }

        String token = authHeader.substring(BEARER.length()).trim();
        if (token.startsWith("\"") && token.endsWith("\"") && token.length() > 1) {
            token = token.substring(1, token.length() - 1).trim();
        }

        JwtTokenVerifier.AuthUser user = null;
        try {
            user = tokenVerifier.verify(token);
            if (user != null && blacklistChecker.isRevoked(user.jti())) {
                log.warn("JWT is blacklisted (jti={}) for user {}", user.jti(), user.userName());
                sendUnauthorized(response, "Token has been revoked");
                return;
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
            sendUnauthorized(response, "Token has expired");
            return;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT verification failed: {}", e.getMessage());
            sendUnauthorized(response, "Invalid token: " + e.getMessage());
            return;
        }

        if (user == null || user.userName() == null) {
            sendUnauthorized(response, "Invalid token claims");
            return;
        }

        List<SimpleGrantedAuthority> authorities = user.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user.userName(), null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated user: {} (id={}) with roles: {}", user.userName(), user.userId(), user.roles());

        filterChain.doFilter(
                UserHeaderRequestWrapper.authenticated(request, user.userId(), user.userName(), user.roles()),
                response);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\"}", message));
        response.getWriter().flush();
    }
}
