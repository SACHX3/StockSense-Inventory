package com.stocksense.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.stocksense.entity.Supplier;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findByIsActiveTrue();

    @Query("SELECT s FROM Supplier s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%',:keyword,'%')) OR LOWER(s.contactPerson) LIKE LOWER(CONCAT('%',:keyword,'%'))")
    List<Supplier> searchSuppliers(String keyword);
}
