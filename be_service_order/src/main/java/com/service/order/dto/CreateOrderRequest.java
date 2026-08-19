package com.service.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Request tạo đơn hàng từ client.
 */
@Data
public class CreateOrderRequest {

    @NotEmpty(message = "Đơn hàng phải có ít nhất 1 sản phẩm")
    @Valid
    private List<OrderItemRequest> items;

    @NotBlank(message = "Địa chỉ giao hàng không được để trống")
    private String shippingAddress;

    private String note;

    @Data
    public static class OrderItemRequest {

        @Min(value = 1, message = "Product ID không hợp lệ")
        private Long productId;

        @NotBlank(message = "Tên sản phẩm không được để trống")
        private String productName;

        @Min(value = 1, message = "Số lượng phải >= 1")
        private Integer quantity;

        @Min(value = 0, message = "Giá không hợp lệ")
        private Long unitPrice;
    }
}
