package web.com.auth.service;

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
 * Key Redis: {@code blacklist:<token>}
 * </p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final JwtAuthentication jwtAuthentication;

    /**
     * Thêm access token vào blacklist.
     * TTL được tính bằng thời gian còn lại của token để Redis tự xoá sau khi token
     * hết hạn.
     *
     * @param accessToken JWT access token cần thu hồi
     */
    public void blacklist(String accessToken) {
        try {
            Date expiry = jwtAuthentication.parse(accessToken).getExpiration();
            long ttlMillis = expiry.getTime() - System.currentTimeMillis();

            if (ttlMillis <= 0) {
                log.info("Access token already expired, skipping blacklist");
                return;
            }
            String key = BLACKLIST_PREFIX + accessToken;
            redisTemplate.opsForValue().set(key, "revoked", Duration.ofMillis(ttlMillis));
            log.info("Access token blacklisted, TTL: {}ms", ttlMillis);
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
        try {
            String key = BLACKLIST_PREFIX + accessToken;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            // Nếu Redis down, mặc định cho pass để tránh block toàn bộ service
            log.error("Redis unavailable when checking blacklist, allowing request: {}", e.getMessage());
            return false;
        }
    }
}
