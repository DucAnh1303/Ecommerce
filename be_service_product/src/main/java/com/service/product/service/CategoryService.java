package com.service.product.service;

import com.service.product.exception.GenerateException;
import com.service.product.module.dto.CategoryDto;
import com.service.product.module.entity.CategoryEntity;
import com.service.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.service.product.exception.GenerateErrorCode.CATEGORY_ALREADY_EXISTS;
import static com.service.product.exception.GenerateErrorCode.CATEGORY_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(CategoryDto::fromEntity)
                .collect(Collectors.toList());
    }

    public CategoryDto getCategoryById(Long id) {
        CategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new GenerateException(CATEGORY_NOT_FOUND, "Category not found with id: " + id));
        return CategoryDto.fromEntity(entity);
    }

    @Transactional
    public CategoryDto createCategory(CategoryDto dto) {
        if (categoryRepository.existsByCateCode(dto.getCateCode())) {
            throw new GenerateException(CATEGORY_ALREADY_EXISTS,"Category code already exists: " + dto.getCateCode());
        }
        CategoryEntity entity = CategoryEntity.builder()
                .cateName(dto.getCateName())
                .cateCode(dto.getCateCode())
                .cateDes(dto.getCateDes())
                .build();
        return CategoryDto.fromEntity(categoryRepository.save(entity));
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryDto dto) {
        CategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        
        if (!entity.getCateCode().equals(dto.getCateCode()) && categoryRepository.existsByCateCode(dto.getCateCode())) {
            throw new GenerateException(CATEGORY_ALREADY_EXISTS, "Category code already exists: " + dto.getCateCode());
        }

        entity.setCateName(dto.getCateName());
        entity.setCateCode(dto.getCateCode());
        entity.setCateDes(dto.getCateDes());
        return CategoryDto.fromEntity(categoryRepository.save(entity));
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new GenerateException(CATEGORY_NOT_FOUND, "Category not found with id: " + id);
        }
        categoryRepository.deleteById(id);
    }
}
