package com.stocksense.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stocksense.entity.Sale;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByInvoiceNumber(String invoiceNumber);
    Page<Sale> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT s FROM Sale s WHERE s.createdAt BETWEEN :start AND :end ORDER BY s.createdAt DESC")
    List<Sale> findByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.createdAt BETWEEN :start AND :end")
    BigDecimal sumRevenueByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(s) FROM Sale s WHERE s.createdAt BETWEEN :start AND :end")
    long countByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Monthly chart (native SQL - Hibernate 6 compatible)
    @Query(value =
        "SELECT YEAR(s.created_at) as yr, MONTH(s.created_at) as mo, " +
        "SUM(s.total_amount) as revenue, COUNT(s.id) as cnt " +
        "FROM sales s WHERE s.created_at >= :start " +
        "GROUP BY YEAR(s.created_at), MONTH(s.created_at) ORDER BY yr ASC, mo ASC",
        nativeQuery = true)
    List<Object[]> getMonthlyRevenue(@Param("start") LocalDateTime start);

    // Daily sales chart - last 30 days (groups by full date so cross-month is correct)
    @Query(value =
        "SELECT DATE(s.created_at) as sale_date, " +
        "SUM(s.total_amount) as revenue, COUNT(s.id) as cnt " +
        "FROM sales s " +
        "WHERE s.created_at >= :start AND s.created_at <= :end " +
        "GROUP BY DATE(s.created_at) ORDER BY sale_date ASC",
        nativeQuery = true)
    List<Object[]> getDailyRevenue(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
