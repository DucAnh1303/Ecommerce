package com.service.order.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.service.order.entity.OrderEntity;
import com.service.order.entity.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response trả về thông tin đơn hàng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long orderId;
    private Long userId;
    private String userName;
    private OrderStatus status;
    private Long totalAmount;
    private String shippingAddress;
    private String note;
    private String failReason;
    private String paymentMethod;
    private String transactionId;
    private Long paidAmount;
    private LocalDateTime paidAt;
    private List<OrderItemResponse> items;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * Thông báo mô tả trạng thái hiện tại cho client
     */
    private String statusMessage;

    /**
     * Dữ liệu Mock thanh toán giả lập cho môi trường Test (URL thanh toán, chuỗi QR...)
     */
    private MockPaymentInfo mockPaymentInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MockPaymentInfo {
        private String paymentUrl;
        private String qrCodeData;
        private String deepLink;
        private String testInstruction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {

        private Long productId;
        private String productName;
        private Integer quantity;
        private Long unitPrice;
        private Long subtotal;
    }

    public static OrderResponse from(OrderEntity entity) {
        List<OrderItemResponse> items = entity.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .orderId(entity.getId())
                .userId(entity.getUserId())
                .userName(entity.getUserName())
                .status(entity.getStatus())
                .totalAmount(entity.getTotalAmount())
                .shippingAddress(entity.getShippingAddress())
                .note(entity.getNote())
                .failReason(entity.getFailReason())
                .paymentMethod(entity.getPaymentMethod())
                .transactionId(entity.getTransactionId())
                .paidAmount(entity.getPaidAmount())
                .paidAt(entity.getPaidAt())
                .items(items)
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .statusMessage(resolveStatusMessage(entity.getStatus()))
                .build();
    }

    private static String resolveStatusMessage(OrderStatus status) {
        return switch (status) {
            case PENDING ->
                "Đơn hàng vừa được tạo, đang kiểm tra tồn kho...";
            case STOCK_RESERVED ->
                "Tồn kho đã được giữ thành công. Vui lòng tiến hành thanh toán.";
            case PAYMENT_PENDING ->
                "Đang chờ xác nhận thanh toán từ cổng thanh toán / ngân hàng...";
            case COMPLETED ->
                "Đơn hàng đã được thanh toán và xác nhận thành công!";
            case STOCK_FAILED ->
                "Đặt hàng thất bại: Sản phẩm đã hết hàng hoặc không đủ số lượng.";
            case PAYMENT_FAILED ->
                "Thanh toán thất bại, số lượng tồn kho đã được hoàn lại.";
            case ROLLBACK_STOCK ->
                "Đang hoàn trả tồn kho...";
            case CANCELLED ->
                "Đơn hàng đã bị hủy, số lượng tồn kho đã được hoàn trả.";
            case SYSTEM_ERROR ->
                "Hệ thống gặp lỗi, vui lòng thử lại sau.";
        };
    }
}
