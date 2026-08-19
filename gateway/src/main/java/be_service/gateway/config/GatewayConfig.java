package be_service.gateway.config;

import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Cấu hình trung tâm cho Gateway.
 * Quản lý danh sách PUBLIC_PATHS – các route không yêu cầu xác thực JWT.
 */
@Configuration
public class GatewayConfig {

    /**
     * Danh sách các path prefix được phép truy cập công khai (không cần JWT).
     */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/validate",
            "/swagger-ui",
            "/v3/api-docs",
            "/api-docs",
            "/actuator/health"
    );

    /**
     * Kiểm tra xem path có phải là public route không.
     */
    public boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}
