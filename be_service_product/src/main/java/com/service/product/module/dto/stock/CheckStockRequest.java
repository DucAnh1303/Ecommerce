package com.service.product.module.dto.stock;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request kiểm tra nhanh số lượng tồn kho trước khi đặt hàng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckStockRequest {

    @NotEmpty(message = "Danh sách sản phẩm không được rỗng")
    private List<ReserveStockRequest.StockItemDto> items;
}
