package com.service.notice.notice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình WebSocket/STOMP – bind từ prefix {@code notice.websocket}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "notice.websocket")
public class WebSocketProperties {

    /** Endpoint handshake (native WS). SockJS fallback = endpoint + "/sockjs". */
    private String endpoint = "/ws/notice";

    /** Origin pattern được phép kết nối. */
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of("http://localhost:*"));

    /** Chu kỳ (ms) server gửi heartbeat xuống client. */
    private long heartbeatServerSend = 10_000;

    /** Chu kỳ (ms) server mong nhận heartbeat từ client. */
    private long heartbeatServerReceive = 10_000;

    private int messageSizeLimit = 64 * 1024;
    private int sendBufferSizeLimit = 512 * 1024;
    private int sendTimeLimit = 20_000;
    private int timeToFirstMessage = 30_000;

    private int inboundPoolSize = 8;
    private int outboundPoolSize = 8;
}
