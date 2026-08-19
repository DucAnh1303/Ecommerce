package com.service.product.repository;

import com.service.product.module.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findByCategoryId(Long categoryId);

    boolean existsByProNameAndCategoryId(String proName, Long categoryId);

    boolean existsByProNameAndCategoryIdAndIdNot(String proName, Long categoryId, Long id);
}
