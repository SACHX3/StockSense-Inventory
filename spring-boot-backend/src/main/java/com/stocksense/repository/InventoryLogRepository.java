package com.stocksense.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stocksense.entity.InventoryLog;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryLogRepository extends JpaRepository<InventoryLog, Long> {
    Page<InventoryLog> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    @Query("SELECT il FROM InventoryLog il ORDER BY il.createdAt DESC")
    Page<InventoryLog> findAllOrderByCreatedAtDesc(Pageable pageable);

    List<InventoryLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Daily stock-in vs stock-out movement totals for the dashboard combo chart.
    @Query(value =
        "SELECT DATE(il.created_at) as log_date, il.movement_type as mtype, SUM(il.quantity) as qty " +
        "FROM inventory_logs il " +
        "WHERE il.created_at >= :start AND il.created_at <= :end " +
        "AND il.movement_type IN ('STOCK_IN','STOCK_OUT') " +
        "GROUP BY DATE(il.created_at), il.movement_type ORDER BY log_date ASC",
        nativeQuery = true)
    List<Object[]> getDailyMovementTotals(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
