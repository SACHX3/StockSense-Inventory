package com.stocksense.service;

import com.stocksense.entity.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomReportService Tests")
class CustomReportServiceTest {

    @Mock SaleService saleService;
    @Mock ProductService productService;
    @Mock SupplierService supplierService;
    @InjectMocks CustomReportService customReportService;

    @Test
    @DisplayName("TC59 - custom report: builds selected sales columns and walk-in fallback")
    void build_salesReport_mapsSelectedColumns() {
        Sale sale = sale("INV-001", null, new BigDecimal("250.00"), Sale.PaymentMethod.CASH);
        when(saleService.findByDateRange(any(), any())).thenReturn(List.of(sale));

        CustomReportService.CustomReportResult result = customReportService.build(
                "sales", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                List.of("invoice", "customer", "total", "paymentMethod"), "none", "date_desc");

        assertThat(result.title()).isEqualTo("Custom Sales Report");
        assertThat(result.headers()).containsExactly("Invoice", "Customer", "Total", "Payment Method");
        assertThat(result.rows()).containsExactly(List.of("INV-001", "Walk-in Customer", "250.00", "CASH"));
    }

    @Test
    @DisplayName("TC60 - custom report: calculates inventory stock value")
    void build_inventoryReport_calculatesStockValue() {
        Product product = new Product();
        product.setName("Coffee");
        product.setSku("COF-01");
        product.setQuantity(10);
        product.setUnit("pack");
        product.setBuyingPrice(new BigDecimal("55.00"));
        product.setSellingPrice(new BigDecimal("80.00"));
        when(productService.findAllActive()).thenReturn(List.of(product));

        CustomReportService.CustomReportResult result = customReportService.build(
                "inventory", null, null, List.of("name", "stock", "buyPrice", "stockValue"), null, null);

        assertThat(result.headers()).containsExactly("Product", "Stock", "Buy Price", "Stock Value");
        assertThat(result.rows()).containsExactly(List.of("Coffee", "10 pack", "55.00", "550.00"));
    }

    @Test
    @DisplayName("TC61 - custom report: maps supplier active status")
    void build_supplierReport_mapsActiveStatus() {
        Supplier supplier = new Supplier();
        supplier.setName("Ceylon Beverages");
        supplier.setCity("Colombo");
        supplier.setPhone("0712345678");
        supplier.setIsActive(false);
        when(supplierService.findAll()).thenReturn(List.of(supplier));

        CustomReportService.CustomReportResult result = customReportService.build(
                "suppliers", null, null, List.of("name", "city", "active"), null, null);

        assertThat(result.rows()).containsExactly(List.of("Ceylon Beverages", "Colombo", "No"));
    }

    @Test
    @DisplayName("TC62 - custom report: supports total-descending sales sort")
    void build_salesReport_totalDescSort_ordersHighestFirst() {
        Sale low = sale("LOW", "Low", new BigDecimal("10.00"), Sale.PaymentMethod.CASH);
        Sale high = sale("HIGH", "High", new BigDecimal("100.00"), Sale.PaymentMethod.CARD);
        when(saleService.findByDateRange(any(), any())).thenReturn(List.of(low, high));

        CustomReportService.CustomReportResult result = customReportService.build(
                "sales", LocalDate.now().minusDays(1), LocalDate.now(),
                List.of("invoice", "total"), "none", "total_desc");

        assertThat(result.rows().get(0)).containsExactly("HIGH", "100.00");
    }

    private Sale sale(String invoice, String customer, BigDecimal total, Sale.PaymentMethod method) {
        Sale sale = new Sale();
        sale.setInvoiceNumber(invoice);
        sale.setCustomerName(customer);
        sale.setSubtotal(total);
        sale.setDiscountAmount(BigDecimal.ZERO);
        sale.setTotalAmount(total);
        sale.setPaymentMethod(method);
        sale.setPaymentStatus(Sale.PaymentStatus.PAID);
        sale.setCreatedAt(LocalDateTime.now());
        return sale;
    }
}
