package com.service.order.event;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event publish khi thanh toán thành công. Order Service tự consume để cập nhật
 * trạng thái COMPLETED.
 *
 * Topic: order.payment.success
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessEvent {

    private Long orderId;
    private Long userId;
    private Long totalAmount;
    private Long paidAmount;
    private String paymentMethod;
    private String transactionId;
    private LocalDateTime paidAt;
    @Builder.Default
    private LocalDateTime eventTime = LocalDateTime.now();
}
