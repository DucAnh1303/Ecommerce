package com.service.order.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Rate Limiter dùng Resilience4j (Token Bucket in-memory).
 *
 * Cấu hình:
 *  - limitForPeriod = 100: tối đa 100 request/giây
 *  - limitRefreshPeriod = 1s: reset token mỗi giây
 *  - timeoutDuration = 0: nếu vượt limit → fail ngay (không chờ)
 *
 * Khi scale nhiều instance của Order Service, mỗi instance có rate limiter riêng.
 * Tổng throughput = limitForPeriod × số instance.
 * Nếu cần rate limit tập trung, cần chuyển sang Redis Token Bucket.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        return RateLimiterRegistry.ofDefaults();
    }

    /**
     * Rate limiter cho Order create endpoint.
     * 100 request/giây/instance. Vượt quá → 429 Too Many Requests.
     */
    @Bean
    public RateLimiter orderCreateRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config =
                RateLimiterConfig.custom()
                        .limitForPeriod(100)
                        .limitRefreshPeriod(Duration.ofSeconds(1))
                        .timeoutDuration(Duration.ZERO)   // fail fast khi quá limit
                        .build();
        return registry.rateLimiter("orderCreate", config);
    }
}
