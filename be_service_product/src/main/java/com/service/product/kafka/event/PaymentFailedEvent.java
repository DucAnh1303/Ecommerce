package com.service.product.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Event nhận từ Order Service khi payment thất bại – rollback stock
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private Long orderId;
    private Long userId;
    private String reason;
    private List<RollbackItem> itemsToRollback;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RollbackItem {
        private Long productId;
        private Integer quantity;
    }
}
