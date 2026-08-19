package com.service.product.module.dto.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response kiểm tra tồn kho.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckStockResponse {

    private Boolean allAvailable;
    private List<ItemAvailability> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemAvailability {
        private Long productId;
        private String productName;
        private Integer requestedQuantity;
        private Integer currentStock;
        private Boolean isAvailable;
        private String note;
    }
}
