package com.stocksense.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DashboardStats {
    private long totalProducts;
    private long totalSuppliers;
    private long lowStockProducts;
    private long totalUsers;
    private BigDecimal todayRevenue;
    private BigDecimal monthlyRevenue;
    private long todaySalesCount;
    private long monthlySalesCount;
    private List<MonthlyRevenue> monthlyRevenueChart;
    private List<TopProduct> topSellingProducts;
    private List<TopProduct> slowMovingProducts;
    private List<LowStockProduct> lowStockItems;

    // ── New: stock health breakdown (doughnut chart) ───────────────────
    private StockHealth stockHealth;

    // ── New: category performance this-month vs last-month (radar) ─────
    private List<CategoryPerformance> categoryPerformance;

    // ── New: revenue vs cost trend, last 14 days (dual line) ───────────
    private List<RevenueCostPoint> revenueCostTrend;

    // ── New: invoice/OCR review status breakdown (polarArea) ───────────
    private List<StatusCount> invoiceStatusBreakdown;

    // ── New: AI forecast sparkline for one flagged (soonest-stockout) product
    private ForecastSparkline forecastSparkline;

    @Data
    public static class MonthlyRevenue {
        private String month;
        private BigDecimal revenue;
        private long count;
    }

    private List<DailySales> dailySalesChart;

    @Data
    public static class DailySales {
        private int day;
        private String label;
        private BigDecimal revenue;
        private long count;
        // units received / sold that day (for combo chart)
        private long unitsReceived;
        private long unitsSold;
    }

    @Data
    public static class TopProduct {
        private Long productId;
        private String productName;
        private String imagePath;
        private long quantity;
        private BigDecimal revenue;
    }

    @Data
    public static class LowStockProduct {
        private Long id;
        private String name;
        private String sku;
        private String unit;
        private String imagePath;
        private int quantity;
        private int minStockLevel;
    }

    @Data
    public static class StockHealth {
        private long healthy;
        private long low;
        private long critical;
    }

    @Data
    public static class CategoryPerformance {
        private String categoryName;
        private long thisMonthUnits;
        private long lastMonthUnits;
    }

    @Data
    public static class RevenueCostPoint {
        private String label;
        private BigDecimal revenue;
        private BigDecimal cost;
    }

    @Data
    public static class StatusCount {
        private String status;
        private long count;
    }

    @Data
    public static class ForecastSparkline {
        private Long productId;
        private String productName;
        private String imagePath;
        private List<Integer> predictedDemand;
        private List<String> labels;
        private Integer daysUntilStockout;
        private Integer recommendedReorderQty;
        private Double trendPercent;
        /** True when every predicted-demand value is 0 - i.e. the model had no
         * real sales history to learn from, so the numbers above (stockout days,
         * reorder qty, trend) aren't meaningful even though a forecast row exists. */
        private Boolean insufficientData;
    }
}
