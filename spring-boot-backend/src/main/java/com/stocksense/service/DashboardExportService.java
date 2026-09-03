package com.stocksense.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import com.stocksense.dto.response.DashboardStats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static com.stocksense.service.PdfLetterheadUtil.*;
import static com.itextpdf.text.Element.*;

/**
 * Generates a real PDF summary of the dashboard's current data (KPIs, sales &
 * stock movement for the selected range, top-selling products, low stock
 * items) using iText 5, sharing the same letterhead as the Sales/Inventory/
 * Supplier report exports so all StockSense PDFs look like one document family.
 */
@Service
@RequiredArgsConstructor
public class DashboardExportService {
    private final StoreProfileService storeProfileService;

    /** Header/footer text for the letterhead, taken from Store Details. */
    private PdfPageEventHelper storeLetterhead(String reportTitle, String metaLine) {
        com.stocksense.entity.StoreProfile s;
        try {
            s = storeProfileService == null ? null : storeProfileService.get();
        } catch (Exception e) {
            s = null;
        }
        if (s == null) {
            return letterheadEvent(reportTitle, metaLine);   // defaults
        }
        // Sub-line: tagline, phone and address, skipping whatever is blank so an
        // unconfigured shop does not print stray separators.
        StringBuilder sub = new StringBuilder();
        appendPart(sub, s.getTagline());
        appendPart(sub, s.getPhone() != null && !s.getPhone().isBlank() ? "Tel: " + s.getPhone() : null);
        appendPart(sub, s.getAddress());
        StringBuilder footer = new StringBuilder(s.getStoreName() == null ? "StockSense" : s.getStoreName());
        if (s.getPhone() != null && !s.getPhone().isBlank()) footer.append("  \u00b7  ").append(s.getPhone());
        if (s.getEmail() != null && !s.getEmail().isBlank()) footer.append("  \u00b7  ").append(s.getEmail());
        return letterheadEvent(reportTitle, metaLine, s.getStoreName(), sub.toString(), footer.toString());
    }

    private void appendPart(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) return;
        if (sb.length() > 0) sb.append("  \u00b7  ");
        sb.append(part.trim());
    }


    public byte[] exportDashboardPdf(DashboardStats stats, int rangeDays) throws IOException {
        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(storeLetterhead("Dashboard Report", generatedAtLine() + "  ·  last " + rangeDays + " days"));
            doc.open();

            // ── KPI row ──────────────────────────────────────────────────
            PdfPTable kpiTable = new PdfPTable(4);
            kpiTable.setWidthPercentage(100);
            kpiTable.setSpacingAfter(16);
            addKpiCell(kpiTable, "Active products", String.valueOf(stats.getTotalProducts()));
            addKpiCell(kpiTable, "Suppliers", String.valueOf(stats.getTotalSuppliers()));
            addKpiCell(kpiTable, "Today's revenue", formatCurrency(stats.getTodayRevenue()));
            addKpiCell(kpiTable, "Monthly revenue", formatCurrency(stats.getMonthlyRevenue()));
            doc.add(kpiTable);

            // ── Sales & stock movement (respecting selected range) ─────────
            List<DashboardStats.DailySales> daily = stats.getDailySalesChart();
            if (daily != null && !daily.isEmpty()) {
                doc.add(sectionTitle("Sales & Stock Movement (last " + rangeDays + " days)"));

                int windowSize = Math.min(rangeDays, daily.size());
                List<DashboardStats.DailySales> windowed = daily.subList(daily.size() - windowSize, daily.size());

                PdfPTable movementTable = new PdfPTable(4);
                movementTable.setWidthPercentage(100);
                movementTable.setWidths(new float[]{1.4f, 1f, 1.3f, 1.3f});
                movementTable.setSpacingAfter(6);
                addHeaderCell(movementTable, "Date");
                addHeaderCell(movementTable, "Sales", ALIGN_RIGHT);
                addHeaderCell(movementTable, "Units sold", ALIGN_RIGHT);
                addHeaderCell(movementTable, "Units received", ALIGN_RIGHT);

                long totalUnitsSold = 0, totalUnitsReceived = 0;
                BigDecimal totalRevenue = BigDecimal.ZERO;
                boolean zebra = false;
                for (DashboardStats.DailySales d : windowed) {
                    addBodyCell(movementTable, d.getLabel(), ALIGN_LEFT, zebra);
                    addBodyCell(movementTable, formatCurrency(d.getRevenue()), ALIGN_RIGHT, zebra);
                    addBodyCell(movementTable, String.valueOf(d.getUnitsSold()), ALIGN_RIGHT, zebra);
                    addBodyCell(movementTable, String.valueOf(d.getUnitsReceived()), ALIGN_RIGHT, zebra);
                    zebra = !zebra;
                    totalUnitsSold += d.getUnitsSold();
                    totalUnitsReceived += d.getUnitsReceived();
                    if (d.getRevenue() != null) totalRevenue = totalRevenue.add(d.getRevenue());
                }
                addTotalCell(movementTable, "Totals", ALIGN_LEFT, 1);
                addTotalCell(movementTable, formatCurrency(totalRevenue), ALIGN_RIGHT, 1);
                addTotalCell(movementTable, String.valueOf(totalUnitsSold), ALIGN_RIGHT, 1);
                addTotalCell(movementTable, String.valueOf(totalUnitsReceived), ALIGN_RIGHT, 1);
                movementTable.setSpacingAfter(16);
                doc.add(movementTable);
            }

            // ── Top-selling products ────────────────────────────────────────
            List<DashboardStats.TopProduct> top = stats.getTopSellingProducts();
            if (top != null && !top.isEmpty()) {
                doc.add(sectionTitle("Top-Selling Products"));

                PdfPTable topTable = new PdfPTable(3);
                topTable.setWidthPercentage(100);
                topTable.setWidths(new float[]{2f, 1f, 1.2f});
                topTable.setSpacingAfter(16);
                addHeaderCell(topTable, "Product");
                addHeaderCell(topTable, "Units sold", ALIGN_RIGHT);
                addHeaderCell(topTable, "Revenue", ALIGN_RIGHT);
                int shown = 0;
                boolean zebra = false;
                for (DashboardStats.TopProduct p : top) {
                    if (shown++ >= 10) break; // keep the PDF to one readable page section
                    addBodyCell(topTable, p.getProductName(), ALIGN_LEFT, zebra);
                    addBodyCell(topTable, String.valueOf(p.getQuantity()), ALIGN_RIGHT, zebra);
                    addBodyCell(topTable, formatCurrency(p.getRevenue()), ALIGN_RIGHT, zebra);
                    zebra = !zebra;
                }
                doc.add(topTable);
            }

            // ── Low stock items ─────────────────────────────────────────────
            List<DashboardStats.LowStockProduct> lowStock = stats.getLowStockItems();
            if (lowStock != null && !lowStock.isEmpty()) {
                doc.add(sectionTitle("Low Stock & At-Risk Items"));

                PdfPTable lowTable = new PdfPTable(4);
                lowTable.setWidthPercentage(100);
                lowTable.setWidths(new float[]{2f, 1.2f, 1f, 1f});
                addHeaderCell(lowTable, "Product");
                addHeaderCell(lowTable, "SKU");
                addHeaderCell(lowTable, "Stock", ALIGN_RIGHT);
                addHeaderCell(lowTable, "Min level", ALIGN_RIGHT);
                boolean zebra = false;
                for (DashboardStats.LowStockProduct p : lowStock) {
                    addBodyCell(lowTable, p.getName(), ALIGN_LEFT, zebra);
                    addBodyCell(lowTable, p.getSku(), ALIGN_LEFT, zebra);
                    addBodyCell(lowTable, p.getQuantity() + " " + (p.getUnit() != null ? p.getUnit() : ""), ALIGN_RIGHT, zebra);
                    addBodyCell(lowTable, String.valueOf(p.getMinStockLevel()), ALIGN_RIGHT, zebra);
                    zebra = !zebra;
                }
                doc.add(lowTable);
            } else {
                doc.add(new Paragraph("All products are currently well stocked.", FONT_LABEL));
            }

        } catch (DocumentException e) {
            throw new IOException("Failed to generate PDF: " + e.getMessage(), e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }
}
