package com.service.notice.notice.websocket;

import com.service.notice.notice.security.StompPrincipal;
import com.service.notice.notice.topic.BusinessType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Lắng nghe vòng đời session STOMP để duy trì trạng thái online / subscription.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final WebSocketSessionRegistry registry;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (!(event.getUser() instanceof StompPrincipal principal)) {
            return;
        }
        String ip = clientIp(accessor);
        registry.register(accessor.getSessionId(), principal.userId(), ip);
        log.info("[WS] CONNECTED userId={} session={} ip={} | online users={} sessions={}",
                principal.userId(), accessor.getSessionId(), ip,
                registry.onlineUserCount(), registry.sessionCount());
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        BusinessType.fromSubscribeDestination(destination).ifPresent(business ->
                registry.addSubscription(accessor.getSessionId(), accessor.getSubscriptionId(), destination, business));
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        registry.removeSubscription(accessor.getSessionId(), accessor.getSubscriptionId());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        WebSocketSessionRegistry.SessionInfo info = registry.unregister(event.getSessionId());
        if (info == null) {
            return;
        }
        long seconds = Duration.between(info.getConnectedAt(), Instant.now()).toSeconds();
        // CloseStatus 1000 = client chủ động đóng; 1002/1006/... = mất kết nối / heartbeat timeout
        log.info("[WS] DISCONNECTED userId={} session={} status={} duration={}s | online users={}",
                info.getUserId(), event.getSessionId(), event.getCloseStatus(), seconds, registry.onlineUserCount());
    }

    private String clientIp(StompHeaderAccessor accessor) {
        // SessionConnectedEvent bọc CONNECT gốc trong header simpConnectMessage
        Object connect = accessor.getHeader("simpConnectMessage");
        Map<String, Object> attrs = connect instanceof org.springframework.messaging.Message<?> m
                ? StompHeaderAccessor.wrap(m).getSessionAttributes()
                : accessor.getSessionAttributes();
        Object ip = attrs != null ? attrs.get(AuthHandshakeInterceptor.ATTR_CLIENT_IP) : null;
        return ip != null ? ip.toString() : "unknown";
    }
}
