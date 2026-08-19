package com.service.order.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event publish khi đơn hàng được tạo.
 * Product Service lắng nghe event này để reserve tồn kho.
 *
 * Topic: order.created
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private Long orderId;
    private Long userId;
    private String userName;
    private List<OrderItemEvent> items;
    private Long totalAmount;
    private String shippingAddress;
    @Builder.Default
    private LocalDateTime eventTime = LocalDateTime.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemEvent {
        private Long productId;
        private String productName;
        private Integer quantity;
        private Long unitPrice;
    }
}
