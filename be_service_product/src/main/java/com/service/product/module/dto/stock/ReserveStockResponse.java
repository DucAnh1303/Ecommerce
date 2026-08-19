package com.service.product.module.dto.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response kết quả giữ tồn kho xác nhận đơn hàng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveStockResponse {

    private Long orderId;
    private Boolean success;
    private String message;
    private List<ReservedItemInfo> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservedItemInfo {
        private Long productId;
        private String productName;
        private Integer quantity;
        private Integer remainingStock;
    }
}
