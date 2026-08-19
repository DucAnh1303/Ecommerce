package com.service.product.module.entity;

import com.service.product.config.util.CurrencyType;
import com.service.product.module.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity(name = "tb_product")
public class ProductEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "pro_name", nullable = false, length = 255)
    private String proName;
    @Column(name = "pro_price", nullable = false, columnDefinition = "0")
    private long proPrice;
    @Column(name = "pro_stock", nullable = false, columnDefinition = "0")
    private int proStock;
    @Column(name = "pro_des", nullable = false)
    private String proDes;
    @Column(name = "pro_img", nullable = false, length = 2000)
    private String proImg;
    @Column(name = "currency_type", nullable = false, length = 5)
    @Enumerated(EnumType.STRING)
    private CurrencyType currency;
    @Column(name = "category_id")
    private Long categoryId;
}
