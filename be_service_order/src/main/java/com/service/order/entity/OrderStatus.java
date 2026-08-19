package com.service.order.entity;

/**
 * Trạng thái của đơn hàng theo từng bước trong Saga.
 *
 * Luồng thành công: PENDING → STOCK_RESERVED → COMPLETED
 *
 * Luồng thất bại: PENDING → STOCK_FAILED (hết hàng) PENDING → STOCK_RESERVED →
 * PAYMENT_FAILED → ROLLBACK_STOCK
 */
public enum OrderStatus {
    /**
     * Đơn vừa được tạo, đang chờ kiểm tra và giữ tồn kho
     */
    PENDING,
    /**
     * Product Service đã reserve tồn kho thành công, sẵn sàng thanh toán
     */
    STOCK_RESERVED,
    /**
     * Product Service báo không đủ tồn kho (Hết hàng)
     */
    STOCK_FAILED,
    /**
     * Đang chờ cổng thanh toán (VNPAY / Momo / Bank) phản hồi xác nhận
     */
    PAYMENT_PENDING,
    /**
     * Thanh toán thành công, đơn hoàn tất
     */
    COMPLETED,
    /**
     * Thanh toán thất bại, đã hoàn trả lại tồn kho
     */
    PAYMENT_FAILED,
    /**
     * Đang rollback tồn kho (sau payment failed)
     */
    ROLLBACK_STOCK,
    /**
     * Đơn hàng đã bị hủy, tồn kho đã hoàn trả
     */
    CANCELLED,
    /**
     * Hệ thống lỗi nội bộ
     */
    SYSTEM_ERROR
}
