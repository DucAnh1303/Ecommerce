package com.service.product.module.dto;

import com.service.product.config.util.CurrencyType;
import com.service.product.module.entity.ProductEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private Long id;
    private String proName;
    private long proPrice;
    private int proStock;
    private String proDes;
    private String proImg;
    private Long categoryId;
    private CurrencyType currency;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static ProductDto fromEntity(ProductEntity entity) {
        if (Objects.isNull(entity)) {
            return null;
        }
        return ProductDto.builder()
                .id(entity.getId())
                .proName(entity.getProName())
                .proPrice(entity.getProPrice())
                .proStock(entity.getProStock())
                .proDes(entity.getProDes())
                .proImg(entity.getProImg())
                .currency(entity.getCurrency())
                .categoryId(entity.getCategoryId())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();

    }
}
