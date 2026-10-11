package com.service.order.config.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Verify access token RS256 tại chỗ bằng public key – không gọi AUTH-SERVICE.
 */
@Component
public class JwtTokenVerifier {

    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS = "access";
    private static final String ROLES_CLAIM = "roles";
    private static final String USER_ID_CLAIM = "uid";

    private final JwksPublicKeyProvider keyProvider;
    private final ObjectMapper objectMapper;
    private final String issuer;
    private final long clockSkewSeconds;

    public JwtTokenVerifier(JwksPublicKeyProvider keyProvider,
                            ObjectMapper objectMapper,
                            @Value("${jwt.issuer}") String issuer,
                            @Value("${jwt.clock-skew:30s}") Duration clockSkew) {
        this.keyProvider = keyProvider;
        this.objectMapper = objectMapper;
        this.issuer = issuer;
        this.clockSkewSeconds = clockSkew.toSeconds();
    }

    /**
     * @throws JwtException nếu token sai chữ ký / hết hạn / sai issuer / không phải access token
     */
    public AuthUser verify(String token) {
        String kid = extractKid(token);
        PublicKey key = keyProvider.getKey(kid);
        if (key == null) {
            throw new JwtException("Unknown signing key (kid=" + kid + ")");
        }
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .clockSkewSeconds(clockSkewSeconds)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!ACCESS.equals(claims.get(TYPE_CLAIM, String.class))) {
            throw new JwtException("Not an access token");
        }
        Number uid = claims.get(USER_ID_CLAIM, Number.class);
        List<?> rawRoles = claims.get(ROLES_CLAIM, List.class);
        List<String> roles = rawRoles == null ? List.of() : rawRoles.stream().map(String::valueOf).toList();
        return new AuthUser(uid != null ? uid.longValue() : null, claims.getSubject(), roles, claims.getId());
    }

    private String extractKid(String token) {
        int dot = token.indexOf('.');
        if (dot <= 0) {
            throw new MalformedJwtException("Malformed JWT");
        }
        try {
            byte[] headerJson = Base64.getUrlDecoder().decode(token.substring(0, dot));
            JsonNode header = objectMapper.readTree(new String(headerJson, StandardCharsets.UTF_8));
            String kid = header.path("kid").asText(null);
            if (kid == null || kid.isBlank()) {
                throw new MalformedJwtException("JWT header missing kid");
            }
            return kid;
        } catch (JwtException e) {
            throw e;
        } catch (Exception e) {
            throw new MalformedJwtException("Malformed JWT header", e);
        }
    }

    public record AuthUser(Long userId, String userName, List<String> roles, String jti) {
    }
}
