package com.stocksense.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.stocksense.dto.response.SupplierCoverageRow;
import com.stocksense.entity.Product;
import com.stocksense.entity.Sale;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.stocksense.service.PdfLetterheadUtil.*;
import static com.itextpdf.text.Element.*;
import java.util.ArrayList;

/**
 * Generates real, letterhead-branded PDF exports for the Sales, Inventory,
 * and Supplier reports under /reports — sharing the same PdfLetterheadUtil
 * header/footer style as the Dashboard PDF export, so every StockSense PDF
 * looks like one consistent document family.
 */
@Service
@RequiredArgsConstructor
public class ReportExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    // ── Sales report ─────────────────────────────────────────────────────
    public byte[] exportSalesReportPdf(List<Sale> sales, LocalDate from, LocalDate to) throws IOException {
        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            String period = from.format(DATE_FMT) + " – " + to.format(DATE_FMT);
            writer.setPageEvent(letterheadEvent("Sales Report", "Period " + period + "  ·  " + generatedAtLine()));
            doc.open();

            BigDecimal totalRevenue = BigDecimal.ZERO;
            BigDecimal totalDiscount = BigDecimal.ZERO;
            for (Sale s : sales) {
                if (s.getTotalAmount() != null) totalRevenue = totalRevenue.add(s.getTotalAmount());
                if (s.getDiscountAmount() != null) totalDiscount = totalDiscount.add(s.getDiscountAmount());
            }
            BigDecimal avgSale = sales.isEmpty() ? BigDecimal.ZERO
                    : totalRevenue.divide(BigDecimal.valueOf(sales.size()), 2, RoundingMode.HALF_UP);

            PdfPTable kpiTable = new PdfPTable(4);
            kpiTable.setWidthPercentage(100);
            kpiTable.setSpacingAfter(16);
            addKpiCell(kpiTable, "Total sales", String.valueOf(sales.size()));
            addKpiCell(kpiTable, "Total revenue", formatCurrency(totalRevenue));
            addKpiCell(kpiTable, "Total discount", formatCurrency(totalDiscount));
            addKpiCell(kpiTable, "Average sale", formatCurrency(avgSale));
            doc.add(kpiTable);

            doc.add(sectionTitle("Sales Transactions"));

            if (sales.isEmpty()) {
                doc.add(new Paragraph("No sales found for the selected period.", FONT_LABEL));
            } else {
                PdfPTable table = new PdfPTable(7);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{1.5f, 1.6f, 1.1f, 1.1f, 1f, 1.2f, 1.4f});
                addHeaderCell(table, "Invoice");
                addHeaderCell(table, "Customer");
                addHeaderCell(table, "Total", ALIGN_RIGHT);
                addHeaderCell(table, "Payment");
                addHeaderCell(table, "Cashier");
                addHeaderCell(table, "Date");
                addHeaderCell(table, "Status");

                boolean zebra = false;
                for (Sale s : sales) {
                    addBodyCell(table, s.getInvoiceNumber(), ALIGN_LEFT, zebra);
                    addBodyCell(table, s.getCustomerName() != null ? s.getCustomerName() : "Walk-in Customer", ALIGN_LEFT, zebra);
                    addBodyCell(table, formatCurrency(s.getTotalAmount()), ALIGN_RIGHT, zebra);
                    addBodyCell(table, s.getPaymentMethod() != null ? s.getPaymentMethod().name() : "-", ALIGN_LEFT, zebra);
                    addBodyCell(table, s.getCashierName(), ALIGN_LEFT, zebra);
                    addBodyCell(table, s.getCreatedAt() != null ? s.getCreatedAt().format(DATETIME_FMT) : "-", ALIGN_LEFT, zebra);
                    addBodyCell(table, s.getPaymentStatus() != null ? s.getPaymentStatus().name() : "-", ALIGN_LEFT, zebra);
                    zebra = !zebra;
                }
                addTotalCell(table, "Totals", ALIGN_LEFT, 2);
                addTotalCell(table, formatCurrency(totalRevenue), ALIGN_RIGHT, 1);
                addTotalCell(table, "", ALIGN_LEFT, 4);
                doc.add(table);
            }
        } catch (DocumentException e) {
            throw new IOException("Failed to generate sales report PDF: " + e.getMessage(), e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    // ── Inventory report ─────────────────────────────────────────────────
    public byte[] exportInventoryReportPdf(List<Product> products, List<Product> lowStock) throws IOException {
        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(letterheadEvent("Inventory Report", generatedAtLine()));
            doc.open();

            BigDecimal totalValue = BigDecimal.ZERO;
            for (Product p : products) {
                BigDecimal buy = p.getBuyingPrice() != null ? p.getBuyingPrice() : BigDecimal.ZERO;
                int qty = p.getQuantity() != null ? p.getQuantity() : 0;
                totalValue = totalValue.add(buy.multiply(BigDecimal.valueOf(qty)));
            }
            int wellStocked = products.size() - lowStock.size();

            PdfPTable kpiTable = new PdfPTable(4);
            kpiTable.setWidthPercentage(100);
            kpiTable.setSpacingAfter(16);
            addKpiCell(kpiTable, "Total products", String.valueOf(products.size()));
            addKpiCell(kpiTable, "Low stock", String.valueOf(lowStock.size()));
            addKpiCell(kpiTable, "Well stocked", String.valueOf(wellStocked));
            addKpiCell(kpiTable, "Total stock value", formatCurrency(totalValue));
            doc.add(kpiTable);

            if (!lowStock.isEmpty()) {
                doc.add(sectionTitle("Low Stock & Out-of-Stock Items"));
                PdfPTable lowTable = new PdfPTable(5);
                lowTable.setWidthPercentage(100);
                lowTable.setWidths(new float[]{2f, 1.2f, 1f, 1f, 1.1f});
                addHeaderCell(lowTable, "Product");
                addHeaderCell(lowTable, "SKU");
                addHeaderCell(lowTable, "Stock", ALIGN_RIGHT);
                addHeaderCell(lowTable, "Min level", ALIGN_RIGHT);
                addHeaderCell(lowTable, "Status");
                boolean zebra = false;
                for (Product p : lowStock) {
                    int qty = p.getQuantity() != null ? p.getQuantity() : 0;
                    int min = p.getMinStockLevel() != null ? p.getMinStockLevel() : 0;
                    addBodyCell(lowTable, p.getName(), ALIGN_LEFT, zebra);
                    addBodyCell(lowTable, p.getSku(), ALIGN_LEFT, zebra);
                    addBodyCell(lowTable, qty + " " + (p.getUnit() != null ? p.getUnit() : ""), ALIGN_RIGHT, zebra);
                    addBodyCell(lowTable, String.valueOf(min), ALIGN_RIGHT, zebra);
                    addBodyCell(lowTable, qty == 0 ? "Out of stock" : "Low stock", ALIGN_LEFT, zebra);
                    zebra = !zebra;
                }
                lowTable.setSpacingAfter(16);
                doc.add(lowTable);
            }

            doc.add(sectionTitle("All Active Products"));
            if (products.isEmpty()) {
                doc.add(new Paragraph("No active products found.", FONT_LABEL));
            } else {
                PdfPTable table = new PdfPTable(7);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{2f, 1.2f, 1.3f, 1f, 1.1f, 1.1f, 1.2f});
                addHeaderCell(table, "Product");
                addHeaderCell(table, "SKU");
                addHeaderCell(table, "Category");
                addHeaderCell(table, "Stock", ALIGN_RIGHT);
                addHeaderCell(table, "Buy price", ALIGN_RIGHT);
                addHeaderCell(table, "Sell price", ALIGN_RIGHT);
                addHeaderCell(table, "Stock value", ALIGN_RIGHT);

                boolean zebra = false;
                for (Product p : products) {
                    BigDecimal buy = p.getBuyingPrice() != null ? p.getBuyingPrice() : BigDecimal.ZERO;
                    int qty = p.getQuantity() != null ? p.getQuantity() : 0;
                    BigDecimal stockValue = buy.multiply(BigDecimal.valueOf(qty));
                    addBodyCell(table, p.getName(), ALIGN_LEFT, zebra);
                    addBodyCell(table, p.getSku(), ALIGN_LEFT, zebra);
                    addBodyCell(table, p.getCategory() != null ? p.getCategory().getName() : "-", ALIGN_LEFT, zebra);
                    addBodyCell(table, qty + " " + (p.getUnit() != null ? p.getUnit() : ""), ALIGN_RIGHT, zebra);
                    addBodyCell(table, formatCurrency(buy), ALIGN_RIGHT, zebra);
                    addBodyCell(table, formatCurrency(p.getSellingPrice()), ALIGN_RIGHT, zebra);
                    addBodyCell(table, formatCurrency(stockValue), ALIGN_RIGHT, zebra);
                    zebra = !zebra;
                }
                addTotalCell(table, "Total Stock Value", ALIGN_LEFT, 6);
                addTotalCell(table, formatCurrency(totalValue), ALIGN_RIGHT, 1);
                doc.add(table);
            }
        } catch (DocumentException e) {
            throw new IOException("Failed to generate inventory report PDF: " + e.getMessage(), e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    // ── Supplier report ──────────────────────────────────────────────────
    public byte[] exportSupplierReportPdf(List<SupplierCoverageRow> coverageRows,
                                           long activeCount, long inactiveCount,
                                           long cityCount, BigDecimal overallCoverage) throws IOException {
        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(letterheadEvent("Supplier Report", generatedAtLine()));
            doc.open();

            PdfPTable kpiTable = new PdfPTable(4);
            kpiTable.setWidthPercentage(100);
            kpiTable.setSpacingAfter(16);
            addKpiCell(kpiTable, "Total suppliers", String.valueOf(coverageRows.size()));
            addKpiCell(kpiTable, "Active", String.valueOf(activeCount));
            addKpiCell(kpiTable, "Inactive", String.valueOf(inactiveCount));
            addKpiCell(kpiTable, "Cities", String.valueOf(cityCount));
            doc.add(kpiTable);

            doc.add(sectionTitle("Supplier Coverage Analysis — " + overallCoverage + "% overall"));

            if (coverageRows.isEmpty()) {
                doc.add(new Paragraph("No supplier coverage data found.", FONT_LABEL));
            } else {
                PdfPTable table = new PdfPTable(6);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{2f, 1.3f, 1.1f, 1.1f, 1.4f, 1.2f});
                addHeaderCell(table, "Supplier");
                addHeaderCell(table, "Linked products", ALIGN_RIGHT);
                addHeaderCell(table, "Coverage", ALIGN_RIGHT);
                addHeaderCell(table, "Stock units", ALIGN_RIGHT);
                addHeaderCell(table, "Stock value", ALIGN_RIGHT);
                addHeaderCell(table, "Status");

                boolean zebra = false;
                for (SupplierCoverageRow row : coverageRows) {
                    addBodyCell(table, row.getSupplier().getName(), ALIGN_LEFT, zebra);
                    addBodyCell(table, String.valueOf(row.getLinkedProductCount()), ALIGN_RIGHT, zebra);
                    addBodyCell(table, row.getCoveragePercent() + "%", ALIGN_RIGHT, zebra);
                    addBodyCell(table, String.valueOf(row.getTotalUnits()), ALIGN_RIGHT, zebra);
                    addBodyCell(table, formatCurrency(row.getStockValue()), ALIGN_RIGHT, zebra);
                    addBodyCell(table, row.getLinkedProductCount() > 0 ? "Covered" : "No products", ALIGN_LEFT, zebra);
                    zebra = !zebra;
                }
                doc.add(table);
            }
        } catch (DocumentException e) {
            throw new IOException("Failed to generate supplier report PDF: " + e.getMessage(), e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    // ── CSV exports (same filtered data as the PDF exports) ────────────────

    public byte[] exportSalesReportCsv(List<Sale> sales) throws IOException {
        List<String> headers = List.of("Invoice", "Customer", "Subtotal", "Discount", "Total", "Payment Method", "Payment Status", "Cashier", "Date");
        List<List<String>> rows = new ArrayList<>();
        for (Sale s : sales) {
            rows.add(List.of(
                    nullSafe(s.getInvoiceNumber()),
                    s.getCustomerName() != null ? s.getCustomerName() : "Walk-in Customer",
                    plain(s.getSubtotal()),
                    plain(s.getDiscountAmount()),
                    plain(s.getTotalAmount()),
                    s.getPaymentMethod() != null ? s.getPaymentMethod().name() : "",
                    s.getPaymentStatus() != null ? s.getPaymentStatus().name() : "",
                    nullSafe(s.getCashierName()),
                    s.getCreatedAt() != null ? s.getCreatedAt().format(DATETIME_FMT) : ""
            ));
        }
        return CsvExportUtil.toCsv(headers, rows);
    }

    public byte[] exportInventoryReportCsv(List<Product> products) throws IOException {
        List<String> headers = List.of("Product", "SKU", "Category", "Supplier", "Stock", "Unit", "Buy Price", "Sell Price", "Stock Value", "Min Level", "Status");
        List<List<String>> rows = new ArrayList<>();
        for (Product p : products) {
            BigDecimal buy = p.getBuyingPrice() != null ? p.getBuyingPrice() : BigDecimal.ZERO;
            int qty = p.getQuantity() != null ? p.getQuantity() : 0;
            int min = p.getMinStockLevel() != null ? p.getMinStockLevel() : 0;
            BigDecimal stockValue = buy.multiply(BigDecimal.valueOf(qty));
            String status = qty == 0 ? "Out of stock" : (qty <= min ? "Low stock" : "In stock");
            rows.add(List.of(
                    nullSafe(p.getName()),
                    nullSafe(p.getSku()),
                    p.getCategory() != null ? nullSafe(p.getCategory().getName()) : "",
                    p.getSupplier() != null ? nullSafe(p.getSupplier().getName()) : "",
                    String.valueOf(qty),
                    nullSafe(p.getUnit()),
                    plain(buy),
                    plain(p.getSellingPrice()),
                    plain(stockValue),
                    String.valueOf(min),
                    status
            ));
        }
        return CsvExportUtil.toCsv(headers, rows);
    }

    public byte[] exportSupplierReportCsv(List<SupplierCoverageRow> coverageRows) throws IOException {
        List<String> headers = List.of("Supplier", "City", "Active", "Linked Products", "Coverage %", "Stock Units", "Stock Value");
        List<List<String>> rows = new ArrayList<>();
        for (SupplierCoverageRow row : coverageRows) {
            rows.add(List.of(
                    nullSafe(row.getSupplier().getName()),
                    nullSafe(row.getSupplier().getCity()),
                    Boolean.TRUE.equals(row.getSupplier().getIsActive()) ? "Yes" : "No",
                    String.valueOf(row.getLinkedProductCount()),
                    row.getCoveragePercent() != null ? row.getCoveragePercent().toPlainString() : "0",
                    String.valueOf(row.getTotalUnits()),
                    plain(row.getStockValue())
            ));
        }
        return CsvExportUtil.toCsv(headers, rows);
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private String plain(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ── Custom report (generic column set) ──────────────────────────────

    public byte[] exportCustomReportPdf(String title, List<String> headers, List<List<String>> rows) throws IOException {
        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(letterheadEvent(title, generatedAtLine()));
            doc.open();

            doc.add(sectionTitle(title + " (" + rows.size() + " rows)"));

            if (rows.isEmpty()) {
                doc.add(new Paragraph("No data matched the selected filters.", FONT_LABEL));
            } else {
                PdfPTable table = new PdfPTable(Math.max(headers.size(), 1));
                table.setWidthPercentage(100);
                for (String h : headers) addHeaderCell(table, h);
                boolean zebra = false;
                for (List<String> row : rows) {
                    for (String cell : row) addBodyCell(table, cell, ALIGN_LEFT, zebra);
                    zebra = !zebra;
                }
                doc.add(table);
            }
        } catch (DocumentException e) {
            throw new IOException("Failed to generate custom report PDF: " + e.getMessage(), e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }
}
