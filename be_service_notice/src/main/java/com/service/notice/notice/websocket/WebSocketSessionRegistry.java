package com.service.notice.notice.websocket;

import com.service.notice.notice.topic.BusinessType;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Lưu trạng thái realtime (in-memory) của các WebSocket session:
 * ai đang online, mỗi session đang đăng ký những business/destination nào, hoạt động lần cuối khi nào.
 *
 * <p>Được cập nhật bởi {@link WebSocketEventListener} và {@link NotificationWsController}.
 */
@Component
public class WebSocketSessionRegistry {

    /** sessionId → thông tin session. */
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    /** userId → tập sessionId (1 user có thể mở nhiều tab / thiết bị). */
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();

    public void register(String sessionId, Long userId, String clientIp) {
        sessions.put(sessionId, new SessionInfo(sessionId, userId, clientIp));
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public SessionInfo unregister(String sessionId) {
        SessionInfo info = sessions.remove(sessionId);
        if (info != null) {
            userSessions.computeIfPresent(info.getUserId(), (k, set) -> {
                set.remove(sessionId);
                return set.isEmpty() ? null : set;
            });
        }
        return info;
    }

    public void addSubscription(String sessionId, String subscriptionId, String destination, BusinessType business) {
        SessionInfo info = sessions.get(sessionId);
        if (info != null) {
            info.subscriptions.put(subscriptionId, new Subscription(destination, business, Instant.now()));
            info.touch();
        }
    }

    public void removeSubscription(String sessionId, String subscriptionId) {
        SessionInfo info = sessions.get(sessionId);
        if (info != null) {
            info.subscriptions.remove(subscriptionId);
            info.touch();
        }
    }

    public void touch(String sessionId) {
        SessionInfo info = sessions.get(sessionId);
        if (info != null) {
            info.touch();
        }
    }

    public boolean isOnline(Long userId) {
        return userSessions.containsKey(userId);
    }

    /** User có ít nhất 1 session đang SUBSCRIBE business này không. */
    public boolean isSubscribed(Long userId, BusinessType business) {
        Set<String> ids = userSessions.get(userId);
        if (ids == null) {
            return false;
        }
        return ids.stream().map(sessions::get)
                .anyMatch(s -> s != null && s.subscriptions.values().stream().anyMatch(sub -> sub.business() == business));
    }

    public int onlineUserCount() {
        return userSessions.size();
    }

    public int sessionCount() {
        return sessions.size();
    }

    public Collection<SessionInfo> allSessions() {
        return sessions.values();
    }

    public Collection<SessionInfo> sessionsOf(Long userId) {
        return userSessions.getOrDefault(userId, Set.of()).stream()
                .map(sessions::get).filter(java.util.Objects::nonNull).toList();
    }

    /** Số subscriber theo từng destination. */
    public Map<String, Long> subscriberStats() {
        return sessions.values().stream()
                .flatMap(s -> s.subscriptions.values().stream())
                .collect(Collectors.groupingBy(Subscription::destination, Collectors.counting()));
    }

    // ─── Model ──────────────────────────────────────────────────────────────────

    @Getter
    public static class SessionInfo {
        private final String sessionId;
        private final Long userId;
        private final String clientIp;
        private final Instant connectedAt = Instant.now();
        private volatile Instant lastActiveAt = Instant.now();
        private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

        SessionInfo(String sessionId, Long userId, String clientIp) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.clientIp = clientIp;
        }

        void touch() {
            this.lastActiveAt = Instant.now();
        }
    }

    public record Subscription(String destination, BusinessType business, Instant subscribedAt) {
    }
}
