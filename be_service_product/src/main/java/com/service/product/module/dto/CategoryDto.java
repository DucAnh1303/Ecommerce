package com.service.product.module.dto;

import com.service.product.module.entity.CategoryEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {
    private Long id;
    private String cateName;
    private String cateCode;
    private String cateDes;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static CategoryDto fromEntity(CategoryEntity entity) {
        if (entity == null) return null;
        return CategoryDto.builder()
                .id(entity.getId())
                .cateName(entity.getCateName())
                .cateCode(entity.getCateCode())
                .cateDes(entity.getCateDes())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
