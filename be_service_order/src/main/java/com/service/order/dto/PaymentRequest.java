package com.service.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request thanh toán đơn hàng từ phía Client / Mobile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    /**
     * Phương thức thanh toán: VNPAY, MOMO, CREDIT_CARD, BANK_TRANSFER, COD, QR_PAY...
     */
    @NotBlank(message = "Phương thức thanh toán không được để trống")
    private String paymentMethod;

    /** Mã ngân hàng hoặc thông tin thêm (nếu thanh toán VNPAY / BANK_TRANSFER: NCB, VCB, MB...) */
    private String bankCode;

    /** Số tài khoản / số thẻ giả lập (ví dụ: 9704198526191432198) */
    private String accountNumber;

    /**
     * Cờ kích hoạt tự động mock thành công ngay lập tức trong 1 bước (Dùng khi test nhanh).
     * Mặc định false: Đơn sẽ chuyển sang PAYMENT_PENDING và chờ Webhook IPN /confirm-payment xác nhận.
     */
    private Boolean mockAutoConfirm;

    /** Ghi chú thanh toán */
    private String note;
}
