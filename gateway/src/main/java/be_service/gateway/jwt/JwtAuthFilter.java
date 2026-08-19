package be_service.gateway.jwt;

import be_service.gateway.config.GatewayConfig;
import be_service.gateway.dto.UserDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Global JWT Authentication Filter cho Spring Cloud Gateway.
 *
 * Luồng xử lý:
 *  1. Kiểm tra xem route có phải public không (login, refresh, swagger...) → cho qua
 *  2. Lấy token từ Authorization header → thiếu/sai format → 401
 *  3. Gọi AUTH-SERVICE POST /api/v1/validate để validate token
 *  4. Nhận UserDTO từ response → gắn X-User-Id, X-User-Name, X-User-Roles vào request
 *  5. Forward request đã có thêm headers xuống downstream service
 *
 * Downstream service đọc headers:
 *  - X-User-Id    : Long (userId)
 *  - X-User-Name  : String (username)
 *  - X-User-Roles : String (comma-separated roles, ví dụ "ADMIN,USER")
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter {

    private final WebClient.Builder webClientBuilder;
    private final GatewayConfig gatewayConfig;
    private final ObjectMapper objectMapper;

    private static final String AUTH_SERVICE_VALIDATE_URL = "http://AUTH-SERVICE/api/v1/auth/validate";
    private static final String EMPTY = "";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Bước 1: Bỏ qua xác thực với public routes
        if (gatewayConfig.isPublicPath(path)) {
            log.debug("Public path, skipping auth: {}", path);
            return chain.filter(exchange);
        }

        // Bước 2: Lấy và kiểm tra Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return buildErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        // Bước 3: Gọi AUTH-SERVICE để validate token và lấy thông tin user
        return webClientBuilder.build()
                .post()
                .uri(AUTH_SERVICE_VALIDATE_URL)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .bodyToMono(UserDTO.class)
                .flatMap(userDTO -> {
                    // Bước 4: Gắn thông tin user vào request headers
                    String roles = userDTO.getRoles() != null ? String.join(",", userDTO.getRoles()) : EMPTY;

                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header("X-User-Id", userDTO.getUserId() != null ? userDTO.getUserId().toString() : EMPTY)
                            .header("X-User-Name", userDTO.getUserName() != null ? userDTO.getUserName() : EMPTY)
                            .header("X-User-Roles", roles)
                            .build();

                    log.debug("Auth success – userId={}, userName={}, roles={}, path={}",
                            userDTO.getUserId(), userDTO.getUserName(), roles, path);

                    // Bước 5: Forward request đã được mutate tới downstream service
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .onErrorResume(err -> {
                    log.warn("Token validation failed for path {}: {}", path, err.getMessage());
                    return buildErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
                });
    }

    /**
     * Trả về JSON error response chuẩn thay vì empty body.
     */
    private Mono<Void> buildErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> errorBody = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "path", exchange.getRequest().getURI().getPath()
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorBody);
        } catch (JsonProcessingException e) {
            bytes = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
