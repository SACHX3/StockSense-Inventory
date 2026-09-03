package com.stocksense.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stocksense.entity.SaleItem;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    /** Line items of one sale - used when clearing seeded demo sales. */
    List<SaleItem> findBySaleId(Long saleId);

    @Query("SELECT si.product.id, si.product.name, SUM(si.quantity) as totalQty, SUM(si.totalPrice) as totalRevenue, si.product.imagePath FROM SaleItem si WHERE si.sale.createdAt BETWEEN :start AND :end GROUP BY si.product.id, si.product.name, si.product.imagePath ORDER BY totalQty DESC")
    List<Object[]> findTopSellingProducts(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT si.product.id, SUM(si.quantity) as totalQty FROM SaleItem si WHERE si.sale.createdAt >= :start GROUP BY si.product.id ORDER BY totalQty DESC")
    List<Object[]> findProductSalesHistory(@Param("start") LocalDateTime start);

    @Query("SELECT si.product.id, si.product.name, SUM(si.quantity) as totalQty FROM SaleItem si WHERE si.sale.createdAt BETWEEN :start AND :end GROUP BY si.product.id, si.product.name ORDER BY totalQty ASC")
    List<Object[]> findSlowMovingProducts(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Category turnover (units sold) for the given date range - used for the
    // "this month vs last month" radar chart on the dashboard.
    @Query("SELECT p.category.name, SUM(si.quantity) FROM SaleItem si JOIN si.product p " +
           "WHERE si.sale.createdAt BETWEEN :start AND :end GROUP BY p.category.name ORDER BY SUM(si.quantity) DESC")
    List<Object[]> findCategoryTurnover(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Day-by-day sold quantity for ONE product - this is the real time series the AI
    // forecasting service (fastapi-service) needs to train on. Native SQL, matching the
    // pattern already used in SaleRepository for date grouping (Hibernate 6 compatible).
    @Query(value =
        "SELECT DATE(s.created_at) as sale_date, SUM(si.quantity) as qty " +
        "FROM sales_items si JOIN sales s ON si.sale_id = s.id " +
        "WHERE si.product_id = :productId AND s.created_at >= :start " +
        "GROUP BY DATE(s.created_at) ORDER BY sale_date ASC",
        nativeQuery = true)
    List<Object[]> findDailySalesForProduct(@Param("productId") Long productId, @Param("start") LocalDateTime start);

    // Day-by-day sold quantity for EVERY product in one round trip. Retraining used
    // to call findDailySalesForProduct once per product - 50 queries before the HTTP
    // request to the AI service even began.
    @Query(value =
        "SELECT si.product_id, DATE(s.created_at) as sale_date, SUM(si.quantity) as qty " +
        "FROM sales_items si JOIN sales s ON si.sale_id = s.id " +
        "WHERE s.created_at >= :start " +
        "GROUP BY si.product_id, DATE(s.created_at) " +
        "ORDER BY si.product_id, sale_date ASC",
        nativeQuery = true)
    List<Object[]> findDailySalesForAllProducts(@Param("start") LocalDateTime start);

    // Day-by-day total units sold across ALL products - used for the dashboard's
    // Sales & Stock Movement chart "Units sold" series (real sale-item quantities,
    // not just inventory-log stock-out totals, which may not be logged for every sale path).
    @Query(value =
        "SELECT DATE(s.created_at) as sale_date, SUM(si.quantity) as qty " +
        "FROM sales_items si JOIN sales s ON si.sale_id = s.id " +
        "WHERE s.created_at >= :start AND s.created_at <= :end " +
        "GROUP BY DATE(s.created_at) ORDER BY sale_date ASC",
        nativeQuery = true)
    List<Object[]> getDailyUnitsSold(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
