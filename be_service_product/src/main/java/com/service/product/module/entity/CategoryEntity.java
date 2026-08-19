package com.service.product.module.entity;

import com.service.product.module.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity(name = "tb_category")
public class CategoryEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cate_name", nullable = false)
    private String cateName;

    @Column(name = "cate_code", nullable = false, unique = true)
    private String cateCode;

    @Column(name = "cate_des")
    private String cateDes;
}
