package com.service.notice.notice.config;

import com.service.notice.notice.websocket.AuthHandshakeInterceptor;
import com.service.notice.notice.websocket.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * Cấu hình WebSocket + STOMP.
 *
 * <pre>
 *  Client ──CONNECT (Bearer JWT)──────────────▶ /ws/notice            (native WS)
 *                                               /ws/notice/sockjs     (SockJS fallback)
 *  Client ──SEND    /app/**  ─────────────────▶ @MessageMapping (NotificationWsController)
 *  Client ◀─MESSAGE /topic/** ──────────────── broadcast (SYSTEM, PRODUCT, ADMIN_ORDER)
 *  Client ◀─MESSAGE /user/queue/** ─────────── riêng từng user (ORDER, PAYMENT, NOTIFICATION...)
 *  Client ◀═HEARTBEAT══════════════════════▶ Server   (mỗi 10s, 2 chiều – giữ kết nối luôn sống)
 * </pre>
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties.class)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String APP_PREFIX = "/app";
    public static final String TOPIC_PREFIX = "/topic";
    public static final String QUEUE_PREFIX = "/queue";
    public static final String USER_PREFIX = "/user";

    private final WebSocketProperties props;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = props.getAllowedOriginPatterns().toArray(String[]::new);

        // 1. Native WebSocket (Postman, mobile, @stomp/stompjs với brokerURL)
        registry.addEndpoint(props.getEndpoint())
                .setAllowedOriginPatterns(origins)
                .addInterceptors(authHandshakeInterceptor);

        // 2. SockJS fallback (trình duyệt/proxy chặn WebSocket → tự hạ xuống xhr-streaming/polling)
        registry.addEndpoint(props.getEndpoint() + "/sockjs")
                .setAllowedOriginPatterns(origins)
                .addInterceptors(authHandshakeInterceptor)
                .withSockJS()
                .setHeartbeatTime(25_000)          // SockJS-level heartbeat (giữ proxy không cắt)
                .setDisconnectDelay(5_000)
                .setSessionCookieNeeded(false);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker + heartbeat 2 chiều.
        // Nếu chạy nhiều instance → thay bằng enableStompBrokerRelay (RabbitMQ/ActiveMQ).
        registry.enableSimpleBroker(TOPIC_PREFIX, QUEUE_PREFIX)
                .setHeartbeatValue(new long[]{props.getHeartbeatServerSend(), props.getHeartbeatServerReceive()})
                .setTaskScheduler(wsHeartbeatScheduler());

        registry.setApplicationDestinationPrefixes(APP_PREFIX);
        registry.setUserDestinationPrefix(USER_PREFIX);
        // Giữ thứ tự message gửi tới cùng 1 session (quan trọng với trạng thái đơn hàng)
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor)
                .taskExecutor()
                .corePoolSize(props.getInboundPoolSize())
                .maxPoolSize(props.getInboundPoolSize() * 2)
                .queueCapacity(1_000);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(props.getOutboundPoolSize())
                .maxPoolSize(props.getOutboundPoolSize() * 2)
                .queueCapacity(1_000);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(props.getMessageSizeLimit())
                .setSendBufferSizeLimit(props.getSendBufferSizeLimit())
                .setSendTimeLimit(props.getSendTimeLimit())
                .setTimeToFirstMessage(props.getTimeToFirstMessage());
    }

    /** Scheduler riêng cho heartbeat của broker. */
    @Bean
    public ThreadPoolTaskScheduler wsHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }
}
