package com.service.product.config.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra access token đã bị logout trong Redis theo jti.
 * Đảm bảo Product Service tự kiểm tra độc lập mà không phụ thuộc Gateway.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklistChecker {

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.blacklist.enabled:true}")
    private boolean enabled;

    @Value("${jwt.blacklist.prefix:blacklist:}")
    private String prefix;

    public boolean isRevoked(String jti) {
        if (!enabled || jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(prefix + jti));
        } catch (Exception e) {
            log.error("Redis unavailable when checking blacklist in Product Service: {}", e.getMessage());
            return false;
        }
    }
}
