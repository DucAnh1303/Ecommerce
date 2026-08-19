package com.service.product.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event publish khi reserve stock thất bại
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockFailedEvent {
    private Long orderId;
    private Long userId;
    private String reason;
    @Builder.Default
    private LocalDateTime eventTime = LocalDateTime.now();
}
