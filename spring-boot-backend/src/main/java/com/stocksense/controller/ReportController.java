package com.stocksense.controller;

import com.stocksense.entity.Sale;
import com.stocksense.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.List;

@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final SaleService     saleService;
    private final ProductService  productService;
    private final SupplierService supplierService;

    @GetMapping
    public String reportsDashboard(Model model) {
        model.addAttribute("pageTitle", "Reports");
        model.addAttribute("monthlyRevenue", saleService.getMonthlyRevenue());
        model.addAttribute("todayRevenue", saleService.getTodayRevenue());
        return "reports/index";
    }

    @GetMapping("/sales")
    public String salesReport(Model model,
                               @RequestParam(required = false) String from,
                               @RequestParam(required = false) String to) {

        // Default: current month
        LocalDate fromDate = (from != null && !from.isBlank())
                ? LocalDate.parse(from)
                : LocalDate.now().withDayOfMonth(1);

        LocalDate toDate = (to != null && !to.isBlank())
                ? LocalDate.parse(to)
                : LocalDate.now();

        LocalDateTime start = fromDate.atStartOfDay();
        LocalDateTime end   = toDate.atTime(23, 59, 59);

        log.info("Sales report filter: {} to {}", start, end);

        // ← KEY FIX: use date range query, not findAll()
        List<Sale> sales = saleService.findByDateRange(start, end);

        model.addAttribute("sales",    sales);
        model.addAttribute("from",     fromDate.toString());
        model.addAttribute("to",       toDate.toString());
        model.addAttribute("pageTitle","Sales Report");
        return "reports/sales";
    }

    @GetMapping("/inventory")
    public String inventoryReport(Model model) {
        model.addAttribute("products", productService.findAllActive());
        model.addAttribute("lowStock", productService.findLowStockProducts());
        model.addAttribute("pageTitle", "Inventory Report");
        return "reports/inventory";
    }

    @GetMapping("/suppliers")
    public String supplierReport(Model model) {
        model.addAttribute("suppliers", supplierService.findAll());
        model.addAttribute("pageTitle", "Supplier Report");
        return "reports/suppliers";
    }
}
