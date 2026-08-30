package com.stocksense.controller;

import com.stocksense.entity.Product;
import com.stocksense.entity.Supplier;
import com.stocksense.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Report Controller Feature Tests")
class ReportControllerFeatureTest {

    private final SaleService saleService = mock(SaleService.class);
    private final ProductService productService = mock(ProductService.class);
    private final SupplierService supplierService = mock(SupplierService.class);
    private final CategoryService categoryService = mock(CategoryService.class);
    private final ReportExportService reportExportService = mock(ReportExportService.class);
    private final CustomReportService customReportService = mock(CustomReportService.class);

    private ReportController controller() {
        return new ReportController(saleService, productService, supplierService,
                categoryService, reportExportService, customReportService);
    }

    @Test
    @DisplayName("TC103 - reports: dashboard loads KPI values")
    void reportsDashboard_loadsKpis() {
        when(saleService.getMonthlyRevenue()).thenReturn(new BigDecimal("1000.00"));
        when(saleService.getTodayRevenue()).thenReturn(new BigDecimal("100.00"));
        when(productService.countActive()).thenReturn(10L);
        when(productService.countLowStock()).thenReturn(2L);
        when(supplierService.findAll()).thenReturn(List.of(new Supplier()));
        var model = new ExtendedModelMap();

        String view = controller().reportsDashboard(model);

        assertThat(view).isEqualTo("reports/index");
        assertThat(model.getAttribute("monthlyRevenue")).isEqualTo(new BigDecimal("1000.00"));
        assertThat(model.getAttribute("totalActiveProducts")).isEqualTo(10L);
    }

    @Test
    @DisplayName("TC104 - reports API: classifies products into stock health groups")
    void hubChartData_classifiesStockHealth() {
        when(saleService.getDailySalesData()).thenReturn(Collections.singletonList(
                new Object[]{"2026-08-29", new BigDecimal("250.00")}));
        Product out = product(0, 10);
        Product low = product(5, 10);
        Product healthy = product(50, 10);
        when(productService.findAllActive()).thenReturn(List.of(out, low, healthy));

        Map<String, Object> result = controller().hubChartData();

        assertThat(result).isNotNull();
        assertThat(result.get("outOfStock")).isEqualTo(1L);
        assertThat(result.get("lowStock")).isEqualTo(1L);
        assertThat(result.get("wellStocked")).isEqualTo(1L);
        assertThat(result.get("revenueLabels")).isEqualTo(List.of("2026-08-29"));
    }

    @Test
    @DisplayName("TC105 - reports: inventory filter returns only low-stock products")
    void inventoryReport_lowStockFilter_appliesFilter() {
        Product low = product(5, 10);
        Product healthy = product(50, 10);
        when(productService.findAllActive()).thenReturn(List.of(low, healthy));
        when(productService.findLowStockProducts()).thenReturn(List.of(low));
        when(categoryService.findAllActive()).thenReturn(List.of());
        when(supplierService.findAllActive()).thenReturn(List.of());
        var model = new ExtendedModelMap();

        String view = controller().inventoryReport(model, null, "low stock", null);

        assertThat(view).isEqualTo("reports/inventory");
        assertThat(model.getAttribute("products")).isEqualTo(List.of(low));
    }

    @Test
    @DisplayName("TC106 - reports export: returns CSV content with download headers")
    void exportSalesCsv_returnsCsvResponse() throws Exception {
        when(saleService.findByDateRange(any(), any())).thenReturn(List.of());
        when(reportExportService.exportSalesReportCsv(List.of()))
                .thenReturn("Invoice,Total\r\n".getBytes(StandardCharsets.UTF_8));

        var response = controller().exportSalesReportCsv(null, null, null, null, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("text/csv"));
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("attachment");
        assertThat(response.getBody()).isNotEmpty();
    }

    private Product product(int quantity, int minimum) {
        Product product = new Product();
        product.setQuantity(quantity);
        product.setMinStockLevel(minimum);
        return product;
    }
}
