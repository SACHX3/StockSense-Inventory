package com.stocksense.service;

import com.stocksense.entity.Product;
import com.stocksense.entity.Sale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Report Export Service Tests")
class ReportExportServiceTest {

    // ReportExportService now takes a StoreProfileService so PDF exports can print
    // the shop's letterhead. These tests only assert on CSV content and on the PDF
    // magic bytes, so a null collaborator is enough: storeLetterhead() falls back to
    // the built-in defaults when the store profile cannot be read.
    private final ReportExportService exportService = new ReportExportService(null);

    @Test
    @DisplayName("TC70 - report export: creates a readable sales CSV")
    void exportSalesReportCsv_containsHeaderAndValues() throws Exception {
        Sale sale = new Sale();
        sale.setInvoiceNumber("INV-100");
        sale.setCustomerName("Shop Customer");
        sale.setSubtotal(new BigDecimal("100.00"));
        sale.setDiscountAmount(new BigDecimal("5.00"));
        sale.setTotalAmount(new BigDecimal("95.00"));
        sale.setPaymentMethod(Sale.PaymentMethod.CARD);
        sale.setPaymentStatus(Sale.PaymentStatus.PAID);

        String csv = new String(exportService.exportSalesReportCsv(List.of(sale)), StandardCharsets.UTF_8);

        assertThat(csv).contains("Invoice,Customer,Subtotal,Discount,Total");
        assertThat(csv).contains("INV-100,Shop Customer,100.00,5.00,95.00,CARD,PAID");
    }

    @Test
    @DisplayName("TC71 - report export: creates a readable inventory CSV with stock status")
    void exportInventoryReportCsv_containsStockStatus() throws Exception {
        Product product = new Product();
        product.setName("Coffee");
        product.setSku("COF-01");
        product.setQuantity(0);
        product.setMinStockLevel(10);
        product.setBuyingPrice(new BigDecimal("50.00"));
        product.setSellingPrice(new BigDecimal("80.00"));

        String csv = new String(exportService.exportInventoryReportCsv(List.of(product)), StandardCharsets.UTF_8);

        assertThat(csv).contains("Product,SKU,Category,Supplier,Stock,Unit");
        assertThat(csv).contains("Coffee,COF-01,,,0,pcs,50.00,80.00,0.00,10,Out of stock");
        assertThat(csv).contains("Out of stock");
    }

    @Test
    @DisplayName("TC72 - report export: produces a PDF for an empty sales report")
    void exportSalesReportPdf_emptyData_returnsPdfBytes() throws Exception {
        byte[] pdf = exportService.exportSalesReportPdf(
                List.of(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("TC73 - report export: produces a PDF for inventory data")
    void exportInventoryReportPdf_returnsPdfBytes() throws Exception {
        Product product = new Product();
        product.setName("Coffee");
        product.setSku("COF-01");
        product.setQuantity(5);
        product.setMinStockLevel(10);

        byte[] pdf = exportService.exportInventoryReportPdf(List.of(product), List.of(product));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }
}
