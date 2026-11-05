package com.service.notice.notice.dto;

import com.service.notice.notice.topic.BusinessType;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Các DTO cho giao tiếp 2 chiều client ⇄ server qua /app/**.
 */
public final class WsPayloads {

    private WsPayloads() {
    }

    /** Client → /app/ping. */
    public record Ping(String clientTime) {
    }

    /** Server → /user/queue/reply (type=PONG). */
    public record Pong(String type, String clientTime, Instant serverTime, String sessionId) {
    }

    /**
     * Client → /app/business/register: khai báo các business màn hình hiện tại cần.
     *
     * @param businesses danh sách business
     * @param resourceIds id cho business có tham số, ví dụ {"PRODUCT": ["15","16"]}
     */
    public record RegisterRequest(@NotEmpty List<BusinessType> businesses,
                                  Map<BusinessType, List<String>> resourceIds) {
    }

    /** Server → /user/queue/reply (type=REGISTERED): danh sách destination client cần SUBSCRIBE. */
    public record RegisterResponse(String type, List<Channel> granted, List<Channel> denied) {
    }

    public record Channel(BusinessType business, String destination, String description) {
    }

    /** Client → /app/notification/ack: xác nhận đã nhận/đọc thông báo. */
    public record AckRequest(@NotEmpty List<String> notificationIds) {
    }

    /** Server → /user/queue/reply (type=ACKED). */
    public record AckResponse(String type, int count, Instant serverTime) {
    }

    /** Server → /user/queue/reply (type=WELCOME) ngay sau khi CONNECT. */
    public record Welcome(String type, Long userId, String sessionId, long heartbeatMs, List<Channel> available) {
    }
}
