package com.service.product.repository;

import com.service.product.module.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
    Optional<CategoryEntity> findByCateCode(String cateCode);
    boolean existsByCateCode(String cateCode);
}
