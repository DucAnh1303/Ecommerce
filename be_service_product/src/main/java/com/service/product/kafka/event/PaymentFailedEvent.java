package com.service.product.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Event nhận từ Order Service khi payment thất bại – rollback stock
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentFailedEvent {
    private Long orderId;
    private Long userId;
    private String reason;
    private List<RollbackItem> itemsToRollback;
    private LocalDateTime eventTime;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RollbackItem {
        private Long productId;
        private Integer quantity;
    }
}
