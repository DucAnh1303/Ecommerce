package com.service.order.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event publish khi Product Service không đủ tồn kho.
 * Order Service lắng nghe để chuyển đơn sang STOCK_FAILED.
 *
 * Topic: order.stock.failed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockFailedEvent {
    private Long orderId;
    private Long userId;
    /** Lý do thất bại (ví dụ: "Product 5 out of stock") */
    private String reason;
    @Builder.Default
    private LocalDateTime eventTime = LocalDateTime.now();
}
