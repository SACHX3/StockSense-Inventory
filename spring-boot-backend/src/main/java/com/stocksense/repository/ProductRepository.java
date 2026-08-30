package com.stocksense.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stocksense.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);
    boolean existsBySku(String sku);

    List<Product> findByIsActiveTrue();
    List<Product> findBySupplierIdAndIsActiveTrueOrderByNameAsc(Long supplierId);
    List<Product> findByCategoryIdAndIsActiveTrue(Long categoryId);

    @Query("SELECT p FROM Product p WHERE p.quantity <= p.minStockLevel AND p.isActive = true")
    List<Product> findLowStockProducts();

    @Query("SELECT p FROM Product p WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%',:keyword,'%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%',:keyword,'%')) OR LOWER(p.barcode) LIKE LOWER(CONCAT('%',:keyword,'%'))) AND p.isActive = true")
    Page<Product> searchProducts(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.isActive = true")
    Page<Product> findAllActiveProducts(Pageable pageable);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.quantity <= p.minStockLevel AND p.isActive = true")
    long countLowStockProducts();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.isActive = true")
    long countActiveProducts();
}
