package web.com.auth.config.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import web.com.auth.dto.UserDTO;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@Getter
@Setter
public class JwtAuthentication {

    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";
    private static final String AUTH_TYPE = "type";
    private static final String AUTH_ROLES = "roles";
    @Value("${jwt.secretKey}")
    private String secretKey;

    @Value("${jwt.accessExp}")
    private long accessExp;

    @Value("${jwt.refreshExp}")
    private long refreshExp;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String generateAccessToken(UserDTO userDTO) {
        return buildToken(userDTO, this.accessExp, ACCESS_TOKEN);
    }

    public String generateRefreshToken(UserDTO userDTO) {
        return buildToken(userDTO, this.refreshExp, REFRESH_TOKEN);
    }

    private String buildToken(UserDTO userDTO, long exp, String type) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(AUTH_TYPE, type);
        claims.put(AUTH_ROLES, userDTO.getRoles());
        return Jwts.builder()
                .subject(userDTO.getUserName())
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + exp))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(this.key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isRefreshToken(String token) {
        return REFRESH_TOKEN.equals(parse(token).get(AUTH_TYPE));
    }

    public String getUsername(String token) {
        return parse(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return false;
            }
            Claims claims = parse(token);
            return ACCESS_TOKEN.equals(claims.get(AUTH_TYPE));
        } catch (Exception e) {
            return false;
        }
    }
}
