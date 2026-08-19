package com.service.order.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event publish khi Product Service reserve tồn kho thành công.
 * Order Service lắng nghe để tiến hành bước thanh toán.
 *
 * Topic: order.stock.reserved
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
