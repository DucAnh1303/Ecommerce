package com.service.notice.notice.controller;

import com.service.notice.notice.dto.NotificationMessage;
import com.service.notice.notice.dto.PushRequest;
import com.service.notice.notice.service.NotificationDispatcher;
import com.service.notice.notice.topic.BusinessType;
import com.service.notice.notice.websocket.WebSocketSessionRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST nội bộ (service-to-service / debug). KHÔNG route qua Gateway.
 */
@RestController
@RequestMapping("/internal/notice")
@RequiredArgsConstructor
public class NotificationInternalController {

    private final NotificationDispatcher dispatcher;
    private final WebSocketSessionRegistry registry;

    /** Đẩy 1 thông báo – tự định tuyến theo scope của business. */
    @PostMapping("/push")
    public ResponseEntity<Map<String, Object>> push(@Valid @RequestBody PushRequest req) {
        NotificationMessage msg = NotificationMessage.builder()
                .business(req.business()).type(req.type())
                .severity(req.severity() != null ? req.severity() : NotificationMessage.Severity.INFO)
                .title(req.title()).content(req.content()).data(req.data())
                .build();

        BusinessType b = req.business();
        if (b.getScope() == BusinessType.Scope.USER) {
            dispatcher.sendToUser(req.userId(), msg);
        } else if (b.isParameterized()) {
            dispatcher.sendToResource(req.resourceId(), msg);
        } else {
            dispatcher.broadcast(msg);
        }
        return ResponseEntity.accepted().body(Map.of("id", msg.getId(), "business", b,
                "online", req.userId() == null || registry.isOnline(req.userId())));
    }

    /** Danh mục business + destination để FE tham khảo. */
    @GetMapping("/businesses")
    public List<Map<String, Object>> businesses() {
        return Arrays.stream(BusinessType.values()).map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("business", b);
            m.put("scope", b.getScope());
            m.put("subscribe", b.subscribeDestination());
            m.put("requiredRoles", b.getRequiredRoles());
            m.put("description", b.getDescription());
            return m;
        }).toList();
    }

    /** Thống kê realtime. */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
                "onlineUsers", registry.onlineUserCount(),
                "sessions", registry.sessionCount(),
                "subscribers", registry.subscriberStats());
    }

    /** Trạng thái online + các subscription của 1 user. */
    @GetMapping("/users/{userId}")
    public Map<String, Object> userStatus(@PathVariable Long userId) {
        return Map.of(
                "userId", userId,
                "online", registry.isOnline(userId),
                "sessions", registry.sessionsOf(userId));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
