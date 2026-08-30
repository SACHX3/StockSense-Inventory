package com.stocksense.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stocksense.entity.ProductCategory;

import java.util.List;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    List<ProductCategory> findByIsActiveTrue();
    boolean existsByName(String name);
}
