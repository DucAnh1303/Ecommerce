package com.service.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request xác nhận thanh toán (từ Cổng thanh toán Webhook / IPN hoặc Admin).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmRequest {

    /** Mã giao dịch từ cổng thanh toán (ví dụ VNPAY txnRef, Momo transId) */
    private String transactionId;

    /** Số tiền thanh toán thực tế */
    private Long paidAmount;

    /** Phương thức thanh toán */
    private String paymentMethod;

    /** Trạng thái thanh toán: true = Thành công, false = Thất bại */
    @NotNull(message = "Trạng thái thanh toán (success) không được để trống")
    private Boolean success;

    /** Lý do thất bại (nếu có) */
    private String failReason;
}
