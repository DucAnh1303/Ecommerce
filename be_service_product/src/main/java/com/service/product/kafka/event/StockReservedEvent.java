package com.service.product.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event publish khi reserve stock thành công
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservedEvent {
    private Long orderId;
    private Long userId;
    private Long totalAmount;
    @Builder.Default
    private LocalDateTime eventTime = LocalDateTime.now();
}
