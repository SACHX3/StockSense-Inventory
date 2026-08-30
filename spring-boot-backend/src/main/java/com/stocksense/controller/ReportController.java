package com.stocksense.controller;

import com.stocksense.dto.response.SupplierCoverageRow;
import com.stocksense.entity.Product;
import com.stocksense.entity.Sale;
import com.stocksense.entity.Supplier;
import com.stocksense.service.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final SaleService     saleService;
    private final ProductService  productService;
    private final SupplierService supplierService;
    private final CategoryService categoryService;
    private final ReportExportService reportExportService;
    private final CustomReportService customReportService;

    @GetMapping
    public String reportsDashboard(Model model) {
        model.addAttribute("pageTitle", "Reports");
        model.addAttribute("monthlyRevenue", saleService.getMonthlyRevenue());
        model.addAttribute("todayRevenue", saleService.getTodayRevenue());
        model.addAttribute("totalActiveProducts", productService.countActive());
        model.addAttribute("lowStockCount", productService.countLowStock());
        model.addAttribute("supplierCount", supplierService.findAll().size());
        return "reports/index";
    }

    // ── Hub overview chart data (real, JSON for Chart.js) ──────────────────

    @GetMapping("/api/hub-charts")
    @ResponseBody
    public java.util.Map<String, Object> hubChartData() {
        List<Object[]> daily = saleService.getDailySalesData();
        List<String> revLabels = new ArrayList<>();
        List<BigDecimal> revValues = new ArrayList<>();
        for (Object[] row : daily) {
            revLabels.add(String.valueOf(row[0]));
            Object rev = row[1];
            revValues.add(rev != null ? new BigDecimal(rev.toString()) : BigDecimal.ZERO);
        }

        List<Product> products = productService.findAllActive();
        long outOfStock = products.stream().filter(p -> p.getQuantity() != null && p.getQuantity() == 0).count();
        long lowStock = products.stream()
                .filter(p -> p.getQuantity() != null && p.getMinStockLevel() != null
                        && p.getQuantity() > 0 && p.getQuantity() <= p.getMinStockLevel())
                .count();
        long wellStocked = products.size() - outOfStock - lowStock;

        return java.util.Map.of(
                "revenueLabels", revLabels,
                "revenueValues", revValues,
                "wellStocked", wellStocked,
                "lowStock", lowStock,
                "outOfStock", outOfStock
        );
    }

    // ── Sales report ─────────────────────────────────────────────────────

    @GetMapping("/sales")
    public String salesReport(Model model,
                               @RequestParam(required = false) String from,
                               @RequestParam(required = false) String to,
                               @RequestParam(required = false) String cashier,
                               @RequestParam(required = false) String paymentMethod,
                               @RequestParam(required = false) String status) {

        List<Sale> sales = filterSales(from, to, cashier, paymentMethod, status);
        LocalDate fromDate = resolveFrom(from);
        LocalDate toDate = resolveTo(to);

        model.addAttribute("sales",    sales);
        model.addAttribute("from",     fromDate.toString());
        model.addAttribute("to",       toDate.toString());
        model.addAttribute("cashier",  cashier);
        model.addAttribute("paymentMethod", paymentMethod);
        model.addAttribute("status",   status);
        model.addAttribute("cashierOptions", distinctCashiers());
        model.addAttribute("pageTitle","Sales Report");
        return "reports/sales";
    }

    @GetMapping("/sales/export-pdf")
    public ResponseEntity<byte[]> exportSalesReportPdf(@RequestParam(required = false) String from,
                                                         @RequestParam(required = false) String to,
                                                         @RequestParam(required = false) String cashier,
                                                         @RequestParam(required = false) String paymentMethod,
                                                         @RequestParam(required = false) String status) {
        try {
            List<Sale> sales = filterSales(from, to, cashier, paymentMethod, status);
            byte[] pdf = reportExportService.exportSalesReportPdf(sales, resolveFrom(from), resolveTo(to));
            return pdfResponse(pdf, "stocksense-sales-report-" + LocalDate.now() + ".pdf");
        } catch (Exception e) {
            log.error("Sales report PDF export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(("PDF export failed: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/sales/export-csv")
    public ResponseEntity<byte[]> exportSalesReportCsv(@RequestParam(required = false) String from,
                                                        @RequestParam(required = false) String to,
                                                        @RequestParam(required = false) String cashier,
                                                        @RequestParam(required = false) String paymentMethod,
                                                        @RequestParam(required = false) String status) {
        try {
            List<Sale> sales = filterSales(from, to, cashier, paymentMethod, status);
            byte[] csv = reportExportService.exportSalesReportCsv(sales);
            return csvResponse(csv, "stocksense-sales-report-" + LocalDate.now() + ".csv");
        } catch (Exception e) {
            log.error("Sales report CSV export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(("CSV export failed: " + e.getMessage()).getBytes());
        }
    }

    // ── Inventory report ─────────────────────────────────────────────────

    @GetMapping("/inventory")
    public String inventoryReport(Model model,
                                   @RequestParam(required = false) Long category,
                                   @RequestParam(required = false) String stockStatus,
                                   @RequestParam(required = false) Long supplier) {
        List<Product> products = filterInventory(category, stockStatus, supplier);
        model.addAttribute("products", products);
        model.addAttribute("lowStock", productService.findLowStockProducts());
        model.addAttribute("category", category);
        model.addAttribute("stockStatus", stockStatus);
        model.addAttribute("supplier", supplier);
        model.addAttribute("categoryOptions", categoryService.findAllActive());
        model.addAttribute("supplierOptions", supplierService.findAllActive());
        model.addAttribute("pageTitle", "Inventory Report");
        return "reports/inventory";
    }

    @GetMapping("/inventory/export-pdf")
    public ResponseEntity<byte[]> exportInventoryReportPdf(@RequestParam(required = false) Long category,
                                                             @RequestParam(required = false) String stockStatus,
                                                             @RequestParam(required = false) Long supplier) {
        try {
            List<Product> products = filterInventory(category, stockStatus, supplier);
            List<Product> lowStock = products.stream()
                    .filter(p -> p.getQuantity() != null && p.getMinStockLevel() != null && p.getQuantity() <= p.getMinStockLevel())
                    .toList();
            byte[] pdf = reportExportService.exportInventoryReportPdf(products, lowStock);
            return pdfResponse(pdf, "stocksense-inventory-report-" + LocalDate.now() + ".pdf");
        } catch (Exception e) {
            log.error("Inventory report PDF export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(("PDF export failed: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/inventory/export-csv")
    public ResponseEntity<byte[]> exportInventoryReportCsv(@RequestParam(required = false) Long category,
                                                             @RequestParam(required = false) String stockStatus,
                                                             @RequestParam(required = false) Long supplier) {
        try {
            List<Product> products = filterInventory(category, stockStatus, supplier);
            byte[] csv = reportExportService.exportInventoryReportCsv(products);
            return csvResponse(csv, "stocksense-inventory-report-" + LocalDate.now() + ".csv");
        } catch (Exception e) {
            log.error("Inventory report CSV export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(("CSV export failed: " + e.getMessage()).getBytes());
        }
    }

    // ── Supplier report ──────────────────────────────────────────────────

    @GetMapping("/suppliers")
    public String supplierReport(Model model,
                                  @RequestParam(required = false) String city,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String coverage) {
        SupplierReportData data = buildSupplierReportData(city, status, coverage);

        model.addAttribute("suppliers", data.suppliers());
        model.addAttribute("activeSupplierCount", data.activeCount());
        model.addAttribute("inactiveSupplierCount", data.inactiveCount());
        model.addAttribute("supplierCityCount", data.cityCount());
        model.addAttribute("coverageRows", data.coverageRows());
        model.addAttribute("totalActiveProducts", data.totalProducts());
        model.addAttribute("coveredProducts", data.coveredProducts());
        model.addAttribute("unassignedProducts", data.totalProducts() - data.coveredProducts());
        model.addAttribute("coveragePercent", data.overallCoverage());
        model.addAttribute("city", city);
        model.addAttribute("status", status);
        model.addAttribute("coverage", coverage);
        model.addAttribute("cityOptions", distinctSupplierCities());
        model.addAttribute("pageTitle", "Supplier Report");
        return "reports/suppliers";
    }

    @GetMapping("/suppliers/export-pdf")
    public ResponseEntity<byte[]> exportSupplierReportPdf(@RequestParam(required = false) String city,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String coverage) {
        try {
            SupplierReportData data = buildSupplierReportData(city, status, coverage);
            byte[] pdf = reportExportService.exportSupplierReportPdf(
                    data.coverageRows(), data.activeCount(), data.inactiveCount(),
                    data.cityCount(), data.overallCoverage());
            return pdfResponse(pdf, "stocksense-supplier-report-" + LocalDate.now() + ".pdf");
        } catch (Exception e) {
            log.error("Supplier report PDF export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(("PDF export failed: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/suppliers/export-csv")
    public ResponseEntity<byte[]> exportSupplierReportCsv(@RequestParam(required = false) String city,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String coverage) {
        try {
            SupplierReportData data = buildSupplierReportData(city, status, coverage);
            byte[] csv = reportExportService.exportSupplierReportCsv(data.coverageRows());
            return csvResponse(csv, "stocksense-supplier-report-" + LocalDate.now() + ".csv");
        } catch (Exception e) {
            log.error("Supplier report CSV export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(("CSV export failed: " + e.getMessage()).getBytes());
        }
    }

    // ── Custom report builder ───────────────────────────────────────────

    @GetMapping("/custom")
    public String customReportBuilder(Model model) {
        model.addAttribute("cashierOptions", distinctCashiers());
        model.addAttribute("categoryOptions", categoryService.findAllActive());
        model.addAttribute("pageTitle", "Custom Report");
        return "reports/custom";
    }

    @GetMapping("/custom/export-pdf")
    public ResponseEntity<byte[]> exportCustomReportPdf(@RequestParam String source,
                                                          @RequestParam(required = false) String from,
                                                          @RequestParam(required = false) String to,
                                                          @RequestParam(required = false) List<String> columns,
                                                          @RequestParam(required = false, defaultValue = "none") String groupBy,
                                                          @RequestParam(required = false, defaultValue = "date_desc") String sort) {
        try {
            CustomReportService.CustomReportResult result = customReportResult(source, from, to, columns, groupBy, sort);
            byte[] pdf = reportExportService.exportCustomReportPdf(result.title(), result.headers(), result.rows());
            return pdfResponse(pdf, "stocksense-custom-report-" + LocalDate.now() + ".pdf");
        } catch (Exception e) {
            log.error("Custom report PDF export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(("PDF export failed: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/custom/export-csv")
    public ResponseEntity<byte[]> exportCustomReportCsv(@RequestParam String source,
                                                         @RequestParam(required = false) String from,
                                                         @RequestParam(required = false) String to,
                                                         @RequestParam(required = false) List<String> columns,
                                                         @RequestParam(required = false, defaultValue = "none") String groupBy,
                                                         @RequestParam(required = false, defaultValue = "date_desc") String sort) {
        try {
            CustomReportService.CustomReportResult result = customReportResult(source, from, to, columns, groupBy, sort);
            byte[] csv = com.stocksense.service.CsvExportUtil.toCsv(result.headers(), result.rows());
            return csvResponse(csv, "stocksense-custom-report-" + LocalDate.now() + ".csv");
        } catch (Exception e) {
            log.error("Custom report CSV export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(("CSV export failed: " + e.getMessage()).getBytes());
        }
    }

    // ── Shared helpers ───────────────────────────────────────────────────

    private CustomReportService.CustomReportResult customReportResult(String source, String from, String to,
            List<String> columns, String groupBy, String sort) {
        LocalDate fromDate = resolveFrom(from);
        LocalDate toDate = resolveTo(to);
        return customReportService.build(source, fromDate, toDate, columns, groupBy, sort);
    }

    private LocalDate resolveFrom(String from) {
        return (from != null && !from.isBlank()) ? LocalDate.parse(from) : LocalDate.now().withDayOfMonth(1);
    }

    private LocalDate resolveTo(String to) {
        return (to != null && !to.isBlank()) ? LocalDate.parse(to) : LocalDate.now();
    }

    private List<Sale> filterSales(String from, String to, String cashier, String paymentMethod, String status) {
        LocalDate fromDate = resolveFrom(from);
        LocalDate toDate = resolveTo(to);
        LocalDateTime start = fromDate.atStartOfDay();
        LocalDateTime end = toDate.atTime(23, 59, 59);

        List<Sale> sales = saleService.findByDateRange(start, end);

        if (cashier != null && !cashier.isBlank() && !"All cashiers".equalsIgnoreCase(cashier)) {
            sales = sales.stream().filter(s -> cashier.equalsIgnoreCase(s.getCashierName())).toList();
        }
        if (paymentMethod != null && !paymentMethod.isBlank() && !"All methods".equalsIgnoreCase(paymentMethod)) {
            sales = sales.stream()
                    .filter(s -> s.getPaymentMethod() != null && s.getPaymentMethod().name().equalsIgnoreCase(paymentMethod))
                    .toList();
        }
        if (status != null && !status.isBlank() && !"All statuses".equalsIgnoreCase(status)) {
            sales = sales.stream()
                    .filter(s -> s.getPaymentStatus() != null && s.getPaymentStatus().name().equalsIgnoreCase(status))
                    .toList();
        }
        return sales;
    }

    private List<Product> filterInventory(Long category, String stockStatus, Long supplier) {
        List<Product> products = productService.findAllActive();

        if (category != null) {
            products = products.stream()
                    .filter(p -> p.getCategory() != null && category.equals(p.getCategory().getId()))
                    .toList();
        }
        if (supplier != null) {
            products = products.stream()
                    .filter(p -> p.getSupplier() != null && supplier.equals(p.getSupplier().getId()))
                    .toList();
        }
        if (stockStatus != null && !stockStatus.isBlank() && !"All stock".equalsIgnoreCase(stockStatus)) {
            products = products.stream().filter(p -> {
                int qty = p.getQuantity() != null ? p.getQuantity() : 0;
                int min = p.getMinStockLevel() != null ? p.getMinStockLevel() : 0;
                return switch (stockStatus.toLowerCase()) {
                    case "in stock" -> qty > min;
                    case "low stock" -> qty > 0 && qty <= min;
                    case "out of stock" -> qty == 0;
                    default -> true;
                };
            }).toList();
        }
        return products;
    }

    private List<String> distinctCashiers() {
        return saleService.findByDateRange(LocalDateTime.now().minusYears(2), LocalDateTime.now())
                .stream()
                .map(Sale::getCashierName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> distinctSupplierCities() {
        return supplierService.findAll().stream()
                .map(Supplier::getCity)
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    private SupplierReportData buildSupplierReportData(String city, String status, String coverage) {
        var suppliers = supplierService.findAll();
        List<Product> products = productService.findAllActive();
        long activeSupplierCount = suppliers.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .count();
        long inactiveSupplierCount = suppliers.size() - activeSupplierCount;
        long supplierCityCount = suppliers.stream()
                .map(s -> s.getCity() == null ? "" : s.getCity().trim().toLowerCase())
                .filter(c -> !c.isBlank())
                .distinct()
                .count();
        long coveredProducts = products.stream().filter(p -> p.getSupplier() != null).count();
        long totalProducts = products.size();
        BigDecimal overallCoverage = totalProducts == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(coveredProducts * 100.0 / totalProducts).setScale(1, RoundingMode.HALF_UP);

        List<Supplier> filteredSuppliers = suppliers;
        if (city != null && !city.isBlank() && !"All cities".equalsIgnoreCase(city)) {
            filteredSuppliers = filteredSuppliers.stream()
                    .filter(s -> city.equalsIgnoreCase(s.getCity()))
                    .toList();
        }
        if (status != null && !status.isBlank() && !"All".equalsIgnoreCase(status)) {
            boolean wantActive = "Active".equalsIgnoreCase(status);
            filteredSuppliers = filteredSuppliers.stream()
                    .filter(s -> Boolean.TRUE.equals(s.getIsActive()) == wantActive)
                    .toList();
        }

        List<SupplierCoverageRow> coverageRows = new ArrayList<>();
        for (var supplier : filteredSuppliers) {
            List<Product> linked = products.stream()
                    .filter(p -> p.getSupplier() != null && supplier.getId().equals(p.getSupplier().getId()))
                    .toList();
            long units = linked.stream().mapToLong(p -> p.getQuantity() == null ? 0 : p.getQuantity()).sum();
            BigDecimal stockValue = linked.stream()
                    .map(p -> {
                        BigDecimal price = p.getBuyingPrice() == null ? BigDecimal.ZERO : p.getBuyingPrice();
                        BigDecimal quantity = BigDecimal.valueOf(p.getQuantity() == null ? 0 : p.getQuantity());
                        return price.multiply(quantity);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal supplierCoverage = totalProducts == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(linked.size() * 100.0 / totalProducts).setScale(1, RoundingMode.HALF_UP);
            coverageRows.add(new SupplierCoverageRow(supplier, linked.size(), units, stockValue, supplierCoverage));
        }

        if (coverage != null && !coverage.isBlank() && !"All".equalsIgnoreCase(coverage)) {
            boolean wantCovered = "Covered".equalsIgnoreCase(coverage);
            coverageRows = coverageRows.stream()
                    .filter(r -> (r.getLinkedProductCount() > 0) == wantCovered)
                    .toList();
        }

        return new SupplierReportData(filteredSuppliers, coverageRows, activeSupplierCount,
                inactiveSupplierCount, supplierCityCount, totalProducts, coveredProducts, overallCoverage);
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    private ResponseEntity<byte[]> csvResponse(byte[] csv, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    private record SupplierReportData(
            List<Supplier> suppliers,
            List<SupplierCoverageRow> coverageRows,
            long activeCount,
            long inactiveCount,
            long cityCount,
            long totalProducts,
            long coveredProducts,
            BigDecimal overallCoverage) { }
}
