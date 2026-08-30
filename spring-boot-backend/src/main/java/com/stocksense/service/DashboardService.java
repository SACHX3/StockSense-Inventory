package com.stocksense.service;

import com.stocksense.dto.response.DashboardStats;
import com.stocksense.entity.ForecastResult;
import com.stocksense.entity.Invoice;
import com.stocksense.entity.Product;
import com.stocksense.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final ProductRepository         productRepository;
    private final SupplierRepository        supplierRepository;
    private final UserRepository            userRepository;
    private final SaleRepository            saleRepository;
    private final SaleService               saleService;
    private final SaleItemRepository        saleItemRepository;
    private final ProductService            productService;
    private final InventoryLogRepository    inventoryLogRepository;
    private final InvoiceRepository         invoiceRepository;
    private final ForecastResultRepository  forecastResultRepository;
    private final AIIntegrationService      aiIntegrationService;

    public DashboardStats getDashboardStats() {
        return getDashboardStats(null);
    }

    public DashboardStats getDashboardStats(Long sessionForecastProductId) {
        return getDashboardStats(sessionForecastProductId, 30);
    }

    public DashboardStats getDashboardStats(Long sessionForecastProductId, int rangeDays) {
        DashboardStats stats = new DashboardStats();

        try { stats.setTotalProducts(productService.countActive()); }
        catch (Exception e) { log.warn("countActive err: {}", e.getMessage()); }

        try { stats.setTotalSuppliers(supplierRepository.findByIsActiveTrue().size()); }
        catch (Exception e) { log.warn("suppliers err: {}", e.getMessage()); }

        try { stats.setLowStockProducts(productService.countLowStock()); }
        catch (Exception e) { log.warn("lowStock err: {}", e.getMessage()); }

        try { stats.setTotalUsers(userRepository.count()); }
        catch (Exception e) { log.warn("users err: {}", e.getMessage()); }

        try { stats.setTodayRevenue(nullSafe(saleService.getTodayRevenue())); }
        catch (Exception e) { stats.setTodayRevenue(BigDecimal.ZERO); }

        try { stats.setMonthlyRevenue(nullSafe(saleService.getMonthlyRevenue())); }
        catch (Exception e) { stats.setMonthlyRevenue(BigDecimal.ZERO); }

        try { stats.setTodaySalesCount(saleService.getTodaySalesCount()); }
        catch (Exception e) { log.warn("todaySalesCount err: {}", e.getMessage()); }

        try { stats.setDailySalesChart(buildDailyChart(rangeDays)); }
        catch (Exception e) { stats.setDailySalesChart(new ArrayList<>()); log.error("dailyChart err: {}", e.getMessage(), e); }

        try { stats.setMonthlyRevenueChart(buildMonthlyChart()); }
        catch (Exception e) { stats.setMonthlyRevenueChart(new ArrayList<>()); log.error("monthlyChart err: {}", e.getMessage(), e); }

        try { stats.setTopSellingProducts(buildTopProducts()); }
        catch (Exception e) { stats.setTopSellingProducts(new ArrayList<>()); log.warn("topProducts err: {}", e.getMessage()); }

        try { stats.setLowStockItems(buildLowStockList()); }
        catch (Exception e) { stats.setLowStockItems(new ArrayList<>()); log.warn("lowStockItems err: {}", e.getMessage()); }

        try { stats.setStockHealth(buildStockHealth()); }
        catch (Exception e) { stats.setStockHealth(new DashboardStats.StockHealth()); log.warn("stockHealth err: {}", e.getMessage()); }

        try { stats.setCategoryPerformance(buildCategoryPerformance()); }
        catch (Exception e) { stats.setCategoryPerformance(new ArrayList<>()); log.warn("categoryPerformance err: {}", e.getMessage()); }

        try { stats.setRevenueCostTrend(buildRevenueCostTrend()); }
        catch (Exception e) { stats.setRevenueCostTrend(new ArrayList<>()); log.warn("revenueCostTrend err: {}", e.getMessage()); }

        try { stats.setInvoiceStatusBreakdown(buildInvoiceStatusBreakdown()); }
        catch (Exception e) { stats.setInvoiceStatusBreakdown(new ArrayList<>()); log.warn("invoiceStatusBreakdown err: {}", e.getMessage()); }

        try { stats.setForecastSparkline(buildForecastSparkline(sessionForecastProductId)); }
        catch (Exception e) { log.warn("forecastSparkline err: {}", e.getMessage()); }

        return stats;
    }

    // ── Daily chart: last N days (defaults to 30 for the normal dashboard view) ──
    private List<DashboardStats.DailySales> buildDailyChart(int days) {
        int windowDays = days > 0 ? days : 30;
        LocalDate today = LocalDate.now();
        LocalDate from  = today.minusDays(windowDays - 1);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end   = today.atTime(23, 59, 59);

        List<Object[]> rows = saleRepository.getDailyRevenue(start, end);

        Map<String, BigDecimal> revMap = new HashMap<>();
        Map<String, Long>       cntMap = new HashMap<>();
        for (Object[] row : rows) {
            try {
                String dateKey = row[0].toString().substring(0, 10);
                BigDecimal rev = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
                long       cnt = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                revMap.put(dateKey, rev);
                cntMap.put(dateKey, cnt);
            } catch (Exception e) { log.warn("Daily row parse: {}", e.getMessage()); }
        }

        // Stock movement totals (IN / OUT) for the same window, for the combo chart.
        // "Units received" comes from inventory-log STOCK_IN entries (restocking).
        // "Units sold" comes from real sale-item quantities (sales_items), not the
        // inventory-log STOCK_OUT total, since not every sale path reliably writes a
        // stock-out log entry - sale-item quantity is the authoritative source.
        Map<String, Long> inMap  = new HashMap<>();
        Map<String, Long> outMap = new HashMap<>();
        try {
            List<Object[]> moveRows = inventoryLogRepository.getDailyMovementTotals(start, end);
            for (Object[] row : moveRows) {
                String dateKey = row[0].toString().substring(0, 10);
                String type    = String.valueOf(row[1]);
                long   qty     = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                if ("STOCK_IN".equals(type))  inMap.merge(dateKey, qty, Long::sum);
            }
        } catch (Exception e) { log.warn("movement totals err: {}", e.getMessage()); }

        try {
            List<Object[]> unitsRows = saleItemRepository.getDailyUnitsSold(start, end);
            for (Object[] row : unitsRows) {
                String dateKey = row[0].toString().substring(0, 10);
                long   qty     = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                outMap.merge(dateKey, qty, Long::sum);
            }
        } catch (Exception e) { log.warn("daily units sold err: {}", e.getMessage()); }

        List<DashboardStats.DailySales> chart = new ArrayList<>();
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("dd MMM");

        for (int i = windowDays - 1; i >= 0; i--) {
            LocalDate  date    = today.minusDays(i);
            String     dateKey = date.toString();

            DashboardStats.DailySales ds = new DashboardStats.DailySales();
            ds.setDay(date.getDayOfMonth());
            ds.setLabel(date.format(labelFmt));
            ds.setRevenue(revMap.getOrDefault(dateKey, BigDecimal.ZERO));
            ds.setCount(cntMap.getOrDefault(dateKey, 0L));
            ds.setUnitsReceived(inMap.getOrDefault(dateKey, 0L));
            ds.setUnitsSold(outMap.getOrDefault(dateKey, 0L));
            chart.add(ds);
        }
        return chart;
    }

    // ── Monthly chart: last 12 months ───────────────────────────────
    private List<DashboardStats.MonthlyRevenue> buildMonthlyChart() {
        LocalDateTime start = LocalDateTime.now().minusMonths(11)
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        List<Object[]> rows = saleRepository.getMonthlyRevenue(start);

        Map<String, BigDecimal> revMap = new LinkedHashMap<>();
        Map<String, Long>       cntMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            try {
                int        yr  = ((Number) row[0]).intValue();
                int        mo  = ((Number) row[1]).intValue();
                BigDecimal rev = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
                long       cnt = row[3] != null ? ((Number) row[3]).longValue() : 0L;
                String     key = yr + "-" + mo;
                revMap.put(key, rev);
                cntMap.put(key, cnt);
            } catch (Exception e) { log.warn("Monthly parse: {}", e.getMessage()); }
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");
        List<DashboardStats.MonthlyRevenue> chart = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth ym  = YearMonth.now().minusMonths(i);
            String    key = ym.getYear() + "-" + ym.getMonthValue();
            DashboardStats.MonthlyRevenue mr = new DashboardStats.MonthlyRevenue();
            mr.setMonth(ym.format(fmt));
            mr.setRevenue(revMap.getOrDefault(key, BigDecimal.ZERO));
            mr.setCount(cntMap.getOrDefault(key, 0L));
            chart.add(mr);
        }
        return chart;
    }

    // ── Top selling products ─────────────────────────────────────────
    private List<DashboardStats.TopProduct> buildTopProducts() {
        LocalDateTime start = LocalDateTime.now().minusMonths(3);
        LocalDateTime end   = LocalDateTime.now();
        List<Object[]> rows = saleItemRepository.findTopSellingProducts(start, end);
        List<DashboardStats.TopProduct> list = new ArrayList<>();
        for (int i = 0; i < Math.min(rows.size(), 20); i++) {
            try {
                Object[] row = rows.get(i);
                DashboardStats.TopProduct tp = new DashboardStats.TopProduct();
                tp.setProductId(row[0] != null ? ((Number) row[0]).longValue() : null);
                tp.setProductName(row[1] != null ? row[1].toString() : "Unknown");
                tp.setQuantity(row[2] != null ? ((Number) row[2]).longValue() : 0L);
                tp.setRevenue(row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO);
                tp.setImagePath(row[4] != null ? row[4].toString() : null);
                list.add(tp);
            } catch (Exception e) { log.warn("TopProduct parse: {}", e.getMessage()); }
        }
        return list;
    }

    // ── Low stock list ───────────────────────────────────────────────
    private List<DashboardStats.LowStockProduct> buildLowStockList() {
        List<DashboardStats.LowStockProduct> list = new ArrayList<>();
        for (Product p : productRepository.findLowStockProducts()) {
            DashboardStats.LowStockProduct ls = new DashboardStats.LowStockProduct();
            ls.setId(p.getId());
            ls.setName(p.getName());
            ls.setSku(p.getSku());
            ls.setUnit(p.getUnit());
            ls.setImagePath(p.getImagePath());
            ls.setQuantity(p.getQuantity());
            ls.setMinStockLevel(p.getMinStockLevel());
            list.add(ls);
        }
        return list;
    }

    // ── Stock health breakdown: healthy / low / critical (out of stock) ─
    private DashboardStats.StockHealth buildStockHealth() {
        DashboardStats.StockHealth health = new DashboardStats.StockHealth();
        long healthy = 0, low = 0, critical = 0;
        for (Product p : productRepository.findByIsActiveTrue()) {
            int qty = p.getQuantity() == null ? 0 : p.getQuantity();
            int min = p.getMinStockLevel() == null ? 0 : p.getMinStockLevel();
            if (qty <= 0) critical++;
            else if (qty <= min) low++;
            else healthy++;
        }
        health.setHealthy(healthy);
        health.setLow(low);
        health.setCritical(critical);
        return health;
    }

    // ── Category performance: this-month vs last-month units sold ──────
    private List<DashboardStats.CategoryPerformance> buildCategoryPerformance() {
        LocalDateTime now       = LocalDateTime.now();
        LocalDateTime thisStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime lastStart = thisStart.minusMonths(1);
        LocalDateTime lastEnd   = thisStart.minusSeconds(1);

        Map<String, Long> thisMonth = new LinkedHashMap<>();
        for (Object[] row : saleItemRepository.findCategoryTurnover(thisStart, now)) {
            String name = row[0] != null ? row[0].toString() : "Uncategorised";
            long   qty  = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            thisMonth.merge(name, qty, Long::sum);
        }
        Map<String, Long> lastMonth = new LinkedHashMap<>();
        for (Object[] row : saleItemRepository.findCategoryTurnover(lastStart, lastEnd)) {
            String name = row[0] != null ? row[0].toString() : "Uncategorised";
            long   qty  = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            lastMonth.merge(name, qty, Long::sum);
        }

        Set<String> categories = new LinkedHashSet<>();
        categories.addAll(thisMonth.keySet());
        categories.addAll(lastMonth.keySet());

        List<DashboardStats.CategoryPerformance> list = new ArrayList<>();
        for (String cat : categories) {
            DashboardStats.CategoryPerformance cp = new DashboardStats.CategoryPerformance();
            cp.setCategoryName(cat);
            cp.setThisMonthUnits(thisMonth.getOrDefault(cat, 0L));
            cp.setLastMonthUnits(lastMonth.getOrDefault(cat, 0L));
            list.add(cp);
        }
        // Top 6 by combined activity, keeps the radar readable
        list.sort((a, b) -> Long.compare(
                b.getThisMonthUnits() + b.getLastMonthUnits(),
                a.getThisMonthUnits() + a.getLastMonthUnits()));
        return list.size() > 6 ? list.subList(0, 6) : list;
    }

    // ── Revenue vs cost trend, last 14 days ─────────────────────────────
    private List<DashboardStats.RevenueCostPoint> buildRevenueCostTrend() {
        LocalDate today = LocalDate.now();
        LocalDate from  = today.minusDays(13);
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("dd MMM");

        Map<String, BigDecimal> revMap = new HashMap<>();
        List<Object[]> revRows = saleRepository.getDailyRevenue(from.atStartOfDay(), today.atTime(23, 59, 59));
        for (Object[] row : revRows) {
            String dateKey = row[0].toString().substring(0, 10);
            BigDecimal rev = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
            revMap.put(dateKey, rev);
        }

        // Cost = sum(quantity sold * product buyingPrice) per day, approximated from sale items.
        Map<String, BigDecimal> costMap = new HashMap<>();
        try {
            var sales = saleRepository.findByDateRange(from.atStartOfDay(), today.atTime(23, 59, 59));
            for (var sale : sales) {
                String dateKey = sale.getCreatedAt().toLocalDate().toString();
                BigDecimal dayCost = BigDecimal.ZERO;
                for (var item : sale.getItems()) {
                    BigDecimal buyPrice = item.getProduct() != null && item.getProduct().getBuyingPrice() != null
                            ? item.getProduct().getBuyingPrice() : BigDecimal.ZERO;
                    dayCost = dayCost.add(buyPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
                }
                costMap.merge(dateKey, dayCost, BigDecimal::add);
            }
        } catch (Exception e) { log.warn("cost calc err: {}", e.getMessage()); }

        List<DashboardStats.RevenueCostPoint> chart = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String key = date.toString();
            DashboardStats.RevenueCostPoint p = new DashboardStats.RevenueCostPoint();
            p.setLabel(date.format(labelFmt));
            p.setRevenue(revMap.getOrDefault(key, BigDecimal.ZERO));
            p.setCost(costMap.getOrDefault(key, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            chart.add(p);
        }
        return chart;
    }

    // ── OCR invoice review-status breakdown (polarArea) ─────────────────
    private List<DashboardStats.StatusCount> buildInvoiceStatusBreakdown() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Invoice.OcrStatus s : Invoice.OcrStatus.values()) counts.put(s.name(), 0L);
        for (Invoice inv : invoiceRepository.findAll()) {
            String status = inv.getOcrStatus() != null ? inv.getOcrStatus().name() : "PENDING";
            counts.merge(status, 1L, Long::sum);
        }
        List<DashboardStats.StatusCount> list = new ArrayList<>();
        counts.forEach((k, v) -> {
            DashboardStats.StatusCount sc = new DashboardStats.StatusCount();
            sc.setStatus(k);
            sc.setCount(v);
            list.add(sc);
        });
        return list;
    }

    // ── Forecast sparkline: session-scoped override ─────────────────────
    //    Default (fresh session / login): show the lowest-stock product,
    //    the most urgent restock candidate, even if it has no forecast
    //    yet (shows empty state).
    //    Override: once the user generates a forecast on the Forecasting
    //    page during THIS session, that product takes over the widget
    //    until they forecast a different product or start a new session.
    private DashboardStats.ForecastSparkline buildForecastSparkline(Long sessionForecastProductId) {
        Product target = null;

        if (sessionForecastProductId != null) {
            target = productRepository.findById(sessionForecastProductId).orElse(null);
        }

        // Default: the low-stock product with the smallest quantity - most urgent restock candidate
        if (target == null) {
            List<Product> lowStock = productRepository.findLowStockProducts();
            if (lowStock.isEmpty()) return null;
            target = lowStock.stream()
                    .min(Comparator.comparingInt(p -> p.getQuantity() == null ? 0 : p.getQuantity()))
                    .orElse(null);
        }
        if (target == null) return null;

        List<ForecastResult> results = forecastResultRepository
                .findByProductIdAndForecastDateAfterOrderByForecastDateAsc(target.getId(), LocalDate.now().minusDays(1));

        // Auto-generate a forecast for the dashboard's default (most-urgent low-stock) item
        // if nobody has run one yet, so the widget always has a real demand trend to show
        // instead of an empty chart. Manual forecasts run from the Forecasting page still
        // take priority via sessionForecastProductId above.
        if (results.isEmpty()) {
            try {
                aiIntegrationService.getForecast(target.getId(), 30);
                results = forecastResultRepository
                        .findByProductIdAndForecastDateAfterOrderByForecastDateAsc(target.getId(), LocalDate.now().minusDays(1));
            } catch (Exception e) {
                log.warn("auto-forecast for default dashboard product {} failed: {}", target.getId(), e.getMessage());
            }
        }

        DashboardStats.ForecastSparkline spark = new DashboardStats.ForecastSparkline();
        spark.setProductId(target.getId());
        spark.setProductName(target.getName());
        spark.setImagePath(target.getImagePath());

        if (!results.isEmpty()) {
            List<Integer> demand = new ArrayList<>();
            List<String>  labels = new ArrayList<>();
            for (ForecastResult r : results) {
                demand.add(r.getPredictedDemand() == null ? 0 : r.getPredictedDemand());
                labels.add(r.getForecastDate() != null ? r.getForecastDate().toString() : "");
            }
            spark.setPredictedDemand(demand);
            spark.setLabels(labels);

            // Estimate days-until-stockout from current quantity and average predicted daily demand
            double avgDemand = demand.stream().mapToInt(Integer::intValue).average().orElse(0);
            int qty = target.getQuantity() == null ? 0 : target.getQuantity();
            spark.setDaysUntilStockout(avgDemand > 0 ? (int) Math.ceil(qty / avgDemand) : null);

            // Recommend a reorder quantity to cover a 3-week (21-day) buffer at the
            // predicted daily demand rate, on top of current stock on hand.
            if (avgDemand > 0) {
                int bufferTarget = (int) Math.ceil(avgDemand * 21);
                spark.setRecommendedReorderQty(Math.max(0, bufferTarget - qty));
            }

            // Trend %: compare the average of the first half of the forecast window vs
            // the second half, so the dashboard can show a real "demand rising/falling"
            // indicator instead of a fabricated number.
            if (demand.size() >= 2) {
                int mid = demand.size() / 2;
                double firstHalfAvg = demand.subList(0, mid).stream().mapToInt(Integer::intValue).average().orElse(0);
                double secondHalfAvg = demand.subList(mid, demand.size()).stream().mapToInt(Integer::intValue).average().orElse(0);
                if (firstHalfAvg > 0) {
                    double pct = ((secondHalfAvg - firstHalfAvg) / firstHalfAvg) * 100;
                    spark.setTrendPercent(Math.round(pct * 10) / 10.0);
                }
            }

            // "Insufficient data" should reflect whether we actually have real sales
            // history to learn from - NOT whether the model's predicted daily average
            // happens to be low. A slow-moving product (e.g. 0.3 units/day) can still have
            // a perfectly valid forecast with real stockout/reorder numbers; flagging it as
            // "not enough data" while showing those same numbers above was self-contradictory.
            // Instead, check actual sales history depth: how many distinct days in the last
            // 90 days had at least one sale of this product.
            long daysWithSales = saleItemRepository
                    .findDailySalesForProduct(target.getId(), LocalDateTime.now().minusDays(90))
                    .size();
            spark.setInsufficientData(daysWithSales < 3);
        } else {
            spark.setPredictedDemand(new ArrayList<>());
            spark.setLabels(new ArrayList<>());
            spark.setDaysUntilStockout(null);
        }
        return spark;
    }

    private BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
