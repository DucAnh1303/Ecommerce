package com.service.notice.notice.websocket;

import com.service.notice.notice.config.WebSocketProperties;
import com.service.notice.notice.dto.WsError;
import com.service.notice.notice.dto.WsPayloads.AckRequest;
import com.service.notice.notice.dto.WsPayloads.AckResponse;
import com.service.notice.notice.dto.WsPayloads.Channel;
import com.service.notice.notice.dto.WsPayloads.Ping;
import com.service.notice.notice.dto.WsPayloads.Pong;
import com.service.notice.notice.dto.WsPayloads.RegisterRequest;
import com.service.notice.notice.dto.WsPayloads.RegisterResponse;
import com.service.notice.notice.dto.WsPayloads.Welcome;
import com.service.notice.notice.security.StompPrincipal;
import com.service.notice.notice.topic.BusinessType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Các lệnh client → server (giao tiếp 2 chiều). Mọi phản hồi trả về {@code /user/queue/reply}
 * và chỉ gửi tới đúng session đã gọi ({@code broadcast = false}).
 *
 * <pre>
 *  SUBSCRIBE /app/channels           → trả ngay Welcome + danh sách channel user được phép
 *  SEND      /app/ping               → PONG          (app-level keep-alive, đo latency)
 *  SEND      /app/business/register  → REGISTERED    (server trả list destination cần SUBSCRIBE)
 *  SEND      /app/notification/ack   → ACKED
 * </pre>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class NotificationWsController {

    private static final String REPLY = "/queue/reply";
    private static final String ERRORS = "/queue/errors";

    private final WebSocketSessionRegistry registry;
    private final WebSocketProperties props;

    /**
     * Request-reply 1 lần: client SUBSCRIBE /app/channels ngay sau CONNECT để biết
     * mình được phép đăng ký những business nào.
     */
    @SubscribeMapping("/channels")
    public Welcome channels(StompPrincipal principal, SimpMessageHeaderAccessor accessor) {
        List<Channel> available = Arrays.stream(BusinessType.values())
                .filter(b -> principal.hasAnyRole(b.getRequiredRoles()))
                .map(this::toChannel)
                .toList();
        return new Welcome("WELCOME", principal.userId(), accessor.getSessionId(),
                props.getHeartbeatServerSend(), available);
    }

    /** App-level ping (bổ sung cho STOMP heartbeat) – cập nhật lastActiveAt. */
    @MessageMapping("/ping")
    @SendToUser(destinations = REPLY, broadcast = false)
    public Pong ping(@Payload(required = false) Ping ping, SimpMessageHeaderAccessor accessor) {
        registry.touch(accessor.getSessionId());
        return new Pong("PONG", ping != null ? ping.clientTime() : null, Instant.now(), accessor.getSessionId());
    }

    /**
     * Client khai báo các business màn hình hiện tại cần (vd: trang chi tiết đơn → ORDER, PAYMENT;
     * trang sản phẩm → PRODUCT với id). Server kiểm tra quyền và trả về destination chính xác
     * để client SUBSCRIBE.
     */
    @MessageMapping("/business/register")
    @SendToUser(destinations = REPLY, broadcast = false)
    public RegisterResponse register(@Valid @Payload RegisterRequest request,
                                     StompPrincipal principal,
                                     SimpMessageHeaderAccessor accessor) {
        registry.touch(accessor.getSessionId());
        List<Channel> granted = new ArrayList<>();
        List<Channel> denied = new ArrayList<>();
        Map<BusinessType, List<String>> ids = request.resourceIds() != null ? request.resourceIds() : Map.of();

        for (BusinessType business : request.businesses()) {
            if (!principal.hasAnyRole(business.getRequiredRoles())) {
                denied.add(toChannel(business));
                continue;
            }
            if (business.isParameterized()) {
                for (String id : ids.getOrDefault(business, List.of())) {
                    String dest = business.resolve(id);
                    if (BusinessType.fromSubscribeDestination(dest).isPresent()) {
                        granted.add(new Channel(business, dest, business.getDescription()));
                    } else {
                        denied.add(new Channel(business, dest, "resourceId không hợp lệ"));
                    }
                }
            } else {
                granted.add(toChannel(business));
            }
        }
        log.info("[WS] REGISTER userId={} session={} granted={} denied={}",
                principal.userId(), accessor.getSessionId(),
                granted.stream().map(Channel::destination).toList(),
                denied.stream().map(Channel::business).toList());
        return new RegisterResponse("REGISTERED", granted, denied);
    }

    /** Client xác nhận đã nhận / đọc thông báo. */
    @MessageMapping("/notification/ack")
    @SendToUser(destinations = REPLY, broadcast = false)
    public AckResponse ack(@Valid @Payload AckRequest request, StompPrincipal principal,
                           SimpMessageHeaderAccessor accessor) {
        registry.touch(accessor.getSessionId());
        // TODO: cập nhật trạng thái READ trong DB khi có lưu trữ thông báo
        log.debug("[WS] ACK userId={} ids={}", principal.userId(), request.notificationIds());
        return new AckResponse("ACKED", request.notificationIds().size(), Instant.now());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = ERRORS, broadcast = false)
    public WsError handleException(Exception e) {
        log.warn("[WS] Lỗi xử lý message: {}", e.getMessage());
        return WsError.of("BAD_REQUEST", e.getMessage());
    }

    private Channel toChannel(BusinessType b) {
        return new Channel(b, b.subscribeDestination(), b.getDescription());
    }
}
