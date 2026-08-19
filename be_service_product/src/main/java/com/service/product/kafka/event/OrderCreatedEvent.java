package com.service.product.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Event nhận từ Order Service khi đơn hàng được tạo.
 * Product Service dùng để reserve tồn kho.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private Long orderId;
    private Long userId;
    private String userName;
    private List<OrderItemEvent> items;
    private Long totalAmount;
    private String shippingAddress;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemEvent {
        private Long productId;
        private String productName;
        private Integer quantity;
        private Long unitPrice;
    }
}
