package com.service.order.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event publish khi thanh toán thất bại.
 * Product Service lắng nghe để rollback tồn kho đã reserve.
 * Order Service lắng nghe để cập nhật trạng thái PAYMENT_FAILED.
 *
 * Topic: order.payment.failed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private Long orderId;
    private Long userId;
    private String reason;
    /** Danh sách sản phẩm cần rollback stock */
    private List<RollbackItem> itemsToRollback;
    @Builder.Default
    private LocalDateTime eventTime = LocalDateTime.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RollbackItem {
        private Long productId;
        private Integer quantity;
    }
}
