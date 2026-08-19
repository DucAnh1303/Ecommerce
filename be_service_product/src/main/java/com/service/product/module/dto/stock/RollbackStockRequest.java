package com.service.product.module.dto.stock;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request hoàn trả tồn kho qua API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollbackStockRequest {

    @NotNull(message = "Mã đơn hàng không được để trống")
    private Long orderId;

    private String reason;

    @NotEmpty(message = "Danh sách sản phẩm hoàn kho không được rỗng")
    private List<RollbackItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RollbackItemDto {
        @NotNull(message = "Mã sản phẩm không được để trống")
        private Long productId;

        @NotNull(message = "Số lượng không được để trống")
        private Integer quantity;
    }
}
