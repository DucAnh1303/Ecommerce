package com.service.product.service;

import com.service.product.exception.GenerateErrorCode;
import com.service.product.exception.GenerateException;
import com.service.product.module.dto.ProductDto;
import com.service.product.module.entity.ProductEntity;
import com.service.product.repository.CategoryRepository;
import com.service.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public List<ProductDto> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new GenerateException(GenerateErrorCode.PRODUCT_NOT_FOUND, "Product not found with id: " + id));
        return ProductDto.fromEntity(entity);
    }


    @Transactional(readOnly = true)
    public List<ProductDto> getProductsByCategoryId(Long categoryId) {
        return productRepository.findByCategoryId(categoryId).stream()
                .map(ProductDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductDto createProduct(ProductDto dto) {
        if (dto.getCategoryId() != null && !categoryRepository.existsById(dto.getCategoryId())) {
            throw new GenerateException(GenerateErrorCode.CATEGORY_NOT_FOUND, "Category not found with id: " + dto.getCategoryId());
        }
        if (productRepository.existsByProNameAndCategoryId(dto.getProName(), dto.getCategoryId())) {
            throw new GenerateException(GenerateErrorCode.PRODUCT_ALREADY_EXISTS, "Product already exists with name: " + dto.getProName());
        }
        ProductEntity entity = ProductEntity.builder()
                .proName(dto.getProName())
                .proPrice(dto.getProPrice())
                .proStock(dto.getProStock())
                .proDes(dto.getProDes())
                .proImg(dto.getProImg())
                .categoryId(dto.getCategoryId())
                .build();
        return ProductDto.fromEntity(productRepository.save(entity));
    }

    @Transactional
    public ProductDto updateProduct(Long id, ProductDto dto) {
        ProductEntity entity = productRepository.findById(id).orElseThrow(() -> new GenerateException(GenerateErrorCode.PRODUCT_NOT_FOUND, "Product not found with id: " + id));
        if (dto.getCategoryId() != null && !categoryRepository.existsById(dto.getCategoryId())) {
            throw new GenerateException(GenerateErrorCode.CATEGORY_NOT_FOUND, "Category not found with id: " + dto.getCategoryId());
        }
        if (productRepository.existsByProNameAndCategoryIdAndIdNot(entity.getProName(), entity.getCategoryId(), entity.getId())) {
            throw new GenerateException(GenerateErrorCode.PRODUCT_ALREADY_EXISTS, "Product already exists with name: " + entity.getProName());
        }
        entity.setProName(dto.getProName());
        entity.setProPrice(dto.getProPrice());
        entity.setProStock(dto.getProStock());
        entity.setProDes(dto.getProDes());
        entity.setProImg(dto.getProImg());
        entity.setCategoryId(dto.getCategoryId());
        return ProductDto.fromEntity(productRepository.save(entity));
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new GenerateException(GenerateErrorCode.PRODUCT_NOT_FOUND, "Product not found with id: " + id);
        }
        productRepository.deleteById(id);
    }
}
