package com.service.product.module.dto.stock;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request giữ tồn kho để xác nhận đơn hàng qua API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveStockRequest {

    @NotNull(message = "Mã đơn hàng không được để trống")
    private Long orderId;

    private Long userId;

    private Long totalAmount;

    @NotEmpty(message = "Danh sách sản phẩm không được rỗng")
    private List<StockItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockItemDto {
        @NotNull(message = "Mã sản phẩm không được để trống")
        private Long productId;

        private String productName;

        @NotNull(message = "Số lượng không được để trống")
        private Integer quantity;

        private Long unitPrice;
    }
}
