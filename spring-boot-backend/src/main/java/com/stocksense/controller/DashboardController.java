package com.stocksense.controller;

import com.stocksense.dto.response.ApiResponse;
import com.stocksense.dto.response.DashboardStats;
import com.stocksense.service.DashboardExportService;
import com.stocksense.service.DashboardService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardExportService dashboardExportService;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model, HttpSession session) {
        try {
            Long lastForecastProductId = (Long) session.getAttribute("lastForecastProductId");
            // Load 90 days upfront so the on-page 7d/30d/90d toggle can slice client-side
            // without a server round trip.
            DashboardStats stats = dashboardService.getDashboardStats(lastForecastProductId, 90);
            model.addAttribute("stats", stats);
        } catch (Exception e) {
            log.error("Dashboard load error: {}", e.getMessage(), e);
            // Provide safe empty stats so page still renders
            DashboardStats emptyStats = new DashboardStats();
            emptyStats.setTodayRevenue(BigDecimal.ZERO);
            emptyStats.setMonthlyRevenue(BigDecimal.ZERO);
            emptyStats.setMonthlyRevenueChart(new ArrayList<>());
            emptyStats.setDailySalesChart(new ArrayList<>());
            emptyStats.setTopSellingProducts(new ArrayList<>());
            emptyStats.setLowStockItems(new ArrayList<>());
            emptyStats.setStockHealth(new DashboardStats.StockHealth());
            emptyStats.setCategoryPerformance(new ArrayList<>());
            emptyStats.setRevenueCostTrend(new ArrayList<>());
            emptyStats.setInvoiceStatusBreakdown(new ArrayList<>());
            model.addAttribute("stats", emptyStats);
            model.addAttribute("errorMsg", "Dashboard data partially unavailable: " + e.getMessage());
        }
        model.addAttribute("pageTitle", "Dashboard");
        return "dashboard/index";
    }

    @GetMapping("/api/dashboard/stats")
    @ResponseBody
    public ResponseEntity<ApiResponse<DashboardStats>> getStats(HttpSession session) {
        try {
            Long lastForecastProductId = (Long) session.getAttribute("lastForecastProductId");
            return ResponseEntity.ok(ApiResponse.success("OK", dashboardService.getDashboardStats(lastForecastProductId)));
        } catch (Exception e) {
            log.error("Dashboard API error: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to load stats: " + e.getMessage()));
        }
    }

    // Real PDF export of the dashboard's current data, respecting the 7d/30d/90d
    // range currently selected on the page (passed as a query param by the JS).
    @GetMapping("/api/dashboard/export-pdf")
    public ResponseEntity<byte[]> exportDashboardPdf(
            @RequestParam(defaultValue = "30") int range,
            HttpSession session) {
        try {
            Long lastForecastProductId = (Long) session.getAttribute("lastForecastProductId");
            DashboardStats stats = dashboardService.getDashboardStats(lastForecastProductId, range);
            byte[] pdf = dashboardExportService.exportDashboardPdf(stats, range);

            String filename = "stocksense-dashboard-" + LocalDate.now() + ".pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (Exception e) {
            log.error("Dashboard PDF export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(("PDF export failed: " + e.getMessage()).getBytes());
        }
    }
}
