package web.com.auth.service;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import web.com.auth.config.jwt.JwtAuthentication;

import java.time.Duration;
import java.util.Date;

/**
 * Quản lý blacklist cho Access Token bằng Redis.
 *
 * <p>
 * Khi user logout, access token được đưa vào blacklist với TTL
 * bằng đúng thời gian còn lại của token. Sau khi token hết hạn,
 * Redis tự động xoá khỏi bộ nhớ — không cần cron job dọn dẹp.
 * </p>
 *
 * <p>
 * Key Redis: {@code blacklist:<jti>} – Gateway cũng đọc đúng key này để chặn token
 * đã logout mà không cần gọi AUTH-SERVICE.
 * </p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String ACTIVE_TOKEN_PREFIX = "active_token:";

    private final StringRedisTemplate redisTemplate;
    private final JwtAuthentication jwtAuthentication;

    /**
     * Đăng ký Access Token đang hoạt động cho user.
     * Nếu user đã có token cũ đang hoạt động (re-login), token cũ sẽ bị đưa ngay vào Blacklist.
     *
     * @param username username của người dùng
     * @param newJti   jti của access token mới
     * @param ttl      thời gian sống của access token mới
     */
    public void registerActiveToken(String username, String newJti, Duration ttl) {
        if (username == null || username.isBlank() || newJti == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            String activeKey = ACTIVE_TOKEN_PREFIX + username;
            String oldJti = redisTemplate.opsForValue().get(activeKey);

            if (oldJti != null && !oldJti.isBlank() && !oldJti.equals(newJti)) {
                Long remainingTtl = redisTemplate.getExpire(activeKey, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (remainingTtl != null && remainingTtl > 0) {
                    redisTemplate.opsForValue().set(BLACKLIST_PREFIX + oldJti, "revoked", Duration.ofMillis(remainingTtl));
                    log.info("Re-login detected: Old token for user '{}' (jti={}) has been moved to blacklist with TTL: {}ms",
                            username, oldJti, remainingTtl);
                }
            }

            redisTemplate.opsForValue().set(activeKey, newJti, ttl);
        } catch (Exception e) {
            log.error("Failed to register active token for {}: {}", username, e.getMessage());
        }
    }

    /**
     * Xoá active token của user (khi logout).
     */
    public void removeActiveToken(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(ACTIVE_TOKEN_PREFIX + username);
        } catch (Exception e) {
            log.error("Failed to remove active token for {}: {}", username, e.getMessage());
        }
    }

    /**
     * Thêm access token vào blacklist.
     * TTL được tính bằng thời gian còn lại của token để Redis tự xoá sau khi token
     * hết hạn.
     *
     * @param accessToken JWT access token cần thu hồi
     */
    public void blacklist(String accessToken) {
        try {
            Claims claims = jwtAuthentication.parse(accessToken);
            Date expiry = claims.getExpiration();
            long ttlMillis = expiry.getTime() - System.currentTimeMillis();

            if (ttlMillis <= 0) {
                log.info("Access token already expired, skipping blacklist");
                removeActiveToken(claims.getSubject());
                return;
            }
            String key = BLACKLIST_PREFIX + claims.getId();
            redisTemplate.opsForValue().set(key, "revoked", Duration.ofMillis(ttlMillis));
            removeActiveToken(claims.getSubject());
            log.info("Access token blacklisted (jti={}), TTL: {}ms", claims.getId(), ttlMillis);
        } catch (Exception e) {
            log.error("Failed to blacklist access token: {}", e.getMessage());
        }
    }

    /**
     * Kiểm tra access token có nằm trong blacklist hay không.
     *
     * @param accessToken JWT access token cần kiểm tra
     * @return {@code true} nếu token đã bị thu hồi (nằm trong blacklist)
     */
    public boolean isBlacklisted(String accessToken) {
        String jti;
        try {
            jti = jwtAuthentication.parse(accessToken).getId();
        } catch (Exception e) {
            return false; // token không hợp lệ đã bị validateToken() loại trước đó
        }
        try {
            String key = BLACKLIST_PREFIX + jti;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            // Nếu Redis down, mặc định cho pass để tránh block toàn bộ service
            log.error("Redis unavailable when checking blacklist, allowing request: {}", e.getMessage());
            return false;
        }
    }
}
