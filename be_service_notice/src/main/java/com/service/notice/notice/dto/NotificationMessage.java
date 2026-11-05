package com.service.notice.notice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.service.notice.notice.topic.BusinessType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Payload chuẩn của mọi thông báo đẩy xuống client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationMessage {

    /** ID duy nhất – client dùng để ACK / chống trùng. */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private BusinessType business;

    /** Loại sự kiện cụ thể, ví dụ ORDER_CREATED, PAYMENT_SUCCESS. */
    private String type;

    private Severity severity;

    private String title;
    private String content;

    /** Dữ liệu bổ sung (orderId, amount...). */
    private Map<String, Object> data;

    /** Người nhận (null = broadcast). */
    private Long userId;

    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum Severity { INFO, SUCCESS, WARNING, ERROR }
}
