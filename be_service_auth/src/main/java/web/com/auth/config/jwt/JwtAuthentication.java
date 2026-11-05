package web.com.auth.config.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import web.com.auth.dto.UserDTO;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ký và parse JWT bằng RS256 (RSA private key ký – public key verify).
 *
 * <p>
 * Cấu trúc token:</p>
 * <pre>
 * Access Token :
 *   header : { alg: RS256, kid: &lt;thumbprint&gt; }
 *   payload: { iss, sub=username, aud=[...], uid=userId, roles=[...], type="access", jti, iat, exp }
 *
 * Refresh Token :
 *   header : { alg: RS256, kid: &lt;thumbprint&gt; }
 *   payload: { iss, sub=username, type="refresh", jti, iat, exp }
 * </pre> Các service khác (Gateway/Order/Product) chỉ nhận Access Token và
 * verify bằng public key, nhờ có {@code uid} + {@code roles} trong Access Token
 * nên không cần gọi lại AUTH-SERVICE. Refresh Token chỉ dùng nội bộ tại
 * AUTH-SERVICE để cấp lại Access Token mới.
 */
@Component
@Getter
@Setter
@RequiredArgsConstructor
public class JwtAuthentication {

    public static final String ACCESS_TOKEN = "access";
    public static final String REFRESH_TOKEN = "refresh";
    public static final String AUTH_TYPE = "type";
    public static final String AUTH_ROLES = "roles";
    public static final String AUTH_USER_ID = "uid";

    private final RsaKeyProvider rsaKeyProvider;

    @Value("${jwt.issuer}")
    private String issuer;

    /**
     * Danh sách service được phép dùng token (claim {@code aud}), vd: ORDER,
     * PRODUCT.
     */
    @Value("${jwt.audiences}")
    private List<String> audiences;

    @Value("${jwt.accessExp}")
    private long accessExp;

    @Value("${jwt.refreshExp}")
    private long refreshExp;

    public String generateAccessToken(UserDTO userDTO) {
        long now = System.currentTimeMillis();
        Map<String, Object> claims = new HashMap<>();
        claims.put(AUTH_TYPE, ACCESS_TOKEN);
        claims.put(AUTH_ROLES, userDTO.getRoles());
        claims.put(AUTH_USER_ID, userDTO.getUserId());

        return Jwts.builder()
                .header().keyId(rsaKeyProvider.getKeyId()).and()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience().add(audiences).and()
                .subject(userDTO.getUserName())
                .claims(claims)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessExp))
                .signWith(rsaKeyProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public String generateRefreshToken(UserDTO userDTO) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .header().keyId(rsaKeyProvider.getKeyId()).and()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(userDTO.getUserName())
                .claim(AUTH_TYPE, REFRESH_TOKEN)
                .issuedAt(new Date(now))
                .expiration(new Date(now + refreshExp))
                .signWith(rsaKeyProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(rsaKeyProvider.getPublicKey())
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isRefreshToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return false;
            }
            return REFRESH_TOKEN.equals(parse(token).get(AUTH_TYPE));
        } catch (Exception e) {
            return false;
        }
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
