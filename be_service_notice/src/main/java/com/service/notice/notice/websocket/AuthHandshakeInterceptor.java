package com.service.notice.notice.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Bước HTTP handshake (trước khi nâng cấp lên WebSocket).
 *
 * <p>Trình duyệt không set được header khi mở WebSocket native, nên cho phép truyền token qua
 * query {@code ?access_token=...}. Token được lưu tạm vào session attributes để
 * {@link StompAuthChannelInterceptor} dùng khi nhận frame CONNECT (nếu CONNECT không có header).
 */
@Slf4j
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_TOKEN = "WS_ACCESS_TOKEN";
    public static final String ATTR_CLIENT_IP = "WS_CLIENT_IP";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("access_token");
        if (token != null && !token.isBlank()) {
            attributes.put(ATTR_TOKEN, token);
        }
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        String ip = forwarded != null ? forwarded.split(",")[0].trim()
                : (request.getRemoteAddress() != null ? request.getRemoteAddress().getHostString() : "unknown");
        attributes.put(ATTR_CLIENT_IP, ip);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.warn("[WS] Handshake lỗi: {}", exception.getMessage());
        }
    }
}
