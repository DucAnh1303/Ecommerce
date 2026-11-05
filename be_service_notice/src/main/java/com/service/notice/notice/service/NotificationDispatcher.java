package com.service.notice.notice.service;

import com.service.notice.notice.dto.NotificationMessage;
import com.service.notice.notice.topic.BusinessType;
import com.service.notice.notice.websocket.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Điểm duy nhất để đẩy thông báo xuống client. Định tuyến theo {@link BusinessType#getScope()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry registry;

    /**
     * Gửi riêng cho 1 user theo business (USER scope).
     * Đồng thời copy vào inbox {@link BusinessType#NOTIFICATION} để client chỉ cần nghe 1 kênh nếu muốn.
     */
    public void sendToUser(Long userId, NotificationMessage message) {
        BusinessType business = message.getBusiness();
        if (business == null || business.getScope() != BusinessType.Scope.USER) {
            throw new IllegalArgumentException("Business " + business + " không phải USER scope");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        message.setUserId(userId);

        if (!registry.isOnline(userId)) {
            // TODO: lưu DB / push FCM để gửi lại khi user online
            log.debug("[Notify] userId={} offline – bỏ qua realtime {}:{}", userId, business, message.getType());
            return;
        }
        String user = String.valueOf(userId);
        messagingTemplate.convertAndSendToUser(user, business.getDestination(), message);
        if (business != BusinessType.NOTIFICATION) {
            messagingTemplate.convertAndSendToUser(user, BusinessType.NOTIFICATION.getDestination(), message);
        }
        log.info("[Notify] → user={} {} type={} id={}", userId, business.subscribeDestination(),
                message.getType(), message.getId());
    }

    /** Broadcast tới topic không tham số (SYSTEM, ADMIN_ORDER). */
    public void broadcast(NotificationMessage message) {
        BusinessType business = message.getBusiness();
        requireTopic(business);
        if (business.isParameterized()) {
            throw new IllegalArgumentException("Business " + business + " cần resourceId");
        }
        messagingTemplate.convertAndSend(business.getDestination(), message);
        log.info("[Notify] → broadcast {} type={} id={}", business.getDestination(), message.getType(), message.getId());
    }

    /** Gửi tới topic có tham số, ví dụ /topic/product/15. */
    public void sendToResource(String resourceId, NotificationMessage message) {
        BusinessType business = message.getBusiness();
        requireTopic(business);
        if (!business.isParameterized() || resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("Business " + business + " không hợp lệ với resourceId=" + resourceId);
        }
        String dest = business.resolve(resourceId);
        messagingTemplate.convertAndSend(dest, message);
        log.info("[Notify] → {} type={} id={}", dest, message.getType(), message.getId());
    }

    /** Gửi phản hồi trực tiếp (kênh hệ thống REPLY / ERRORS). */
    public void reply(String user, BusinessType channel, Object payload) {
        messagingTemplate.convertAndSendToUser(user, channel.getDestination(), payload);
    }

    private void requireTopic(BusinessType business) {
        if (business == null || business.getScope() != BusinessType.Scope.TOPIC) {
            throw new IllegalArgumentException("Business " + business + " không phải TOPIC scope");
        }
    }
}
