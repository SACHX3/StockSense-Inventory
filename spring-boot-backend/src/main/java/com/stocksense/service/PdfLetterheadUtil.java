package com.stocksense.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Shared letterhead + table styling used by every generated PDF report
 * (dashboard export, sales/inventory/supplier reports) so all of them look
 * like one consistent document family instead of bespoke one-off layouts.
 */
public final class PdfLetterheadUtil {

    public static final BaseColor PRIMARY   = new BaseColor(0x6d, 0x3f, 0xd8);
    public static final BaseColor PRIMARY_2 = new BaseColor(0x4a, 0x2c, 0x96);
    public static final BaseColor TEXT_2    = new BaseColor(0x5a, 0x5a, 0x6a);
    public static final BaseColor TEXT_3    = new BaseColor(0x8a, 0x8a, 0x9a);
    public static final BaseColor BORDER    = new BaseColor(0xe4, 0xe4, 0xec);
    public static final BaseColor SURFACE_2 = new BaseColor(0xf6, 0xf5, 0xfb);

    public static final Font FONT_TITLE      = new Font(Font.FontFamily.HELVETICA, 19, Font.BOLD, BaseColor.WHITE);
    public static final Font FONT_SUBTITLE   = new Font(Font.FontFamily.HELVETICA, 9.5f, Font.NORMAL, new BaseColor(0xe4, 0xdb, 0xfa));
    public static final Font FONT_META       = new Font(Font.FontFamily.HELVETICA, 8.5f, Font.NORMAL, TEXT_2);
    public static final Font FONT_SECTION    = new Font(Font.FontFamily.HELVETICA, 12.5f, Font.BOLD, new BaseColor(0x22, 0x22, 0x2e));
    public static final Font FONT_LABEL      = new Font(Font.FontFamily.HELVETICA, 8.5f, Font.NORMAL, TEXT_2);
    public static final Font FONT_VALUE      = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, BaseColor.BLACK);
    public static final Font FONT_TABLE_HEAD = new Font(Font.FontFamily.HELVETICA, 8.5f, Font.BOLD, BaseColor.WHITE);
    public static final Font FONT_TABLE_CELL = new Font(Font.FontFamily.HELVETICA, 8.5f, Font.NORMAL, BaseColor.BLACK);
    public static final Font FONT_TABLE_TOTAL= new Font(Font.FontFamily.HELVETICA, 8.5f, Font.BOLD, BaseColor.BLACK);
    public static final Font FONT_FOOTER     = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.NORMAL, TEXT_3);

    private PdfLetterheadUtil() { }

    /** Standard A4 document with generous margins for the letterhead band. */
    public static Document newDocument() {
        return new Document(PageSize.A4, 36, 36, 100, 54);
    }

    /**
     * Draws the purple gradient-style letterhead band (logo mark, app name,
     * report title, generated timestamp) plus a footer with page numbers on
     * every page — via iText's PdfPageEventHelper, so it repeats automatically
     * across multi-page reports.
     */
    public static PdfPageEventHelper letterheadEvent(String reportTitle, String metaLine) {
        return new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter writer, Document document) {
                PdfContentByte cb = writer.getDirectContent();
                Rectangle page = document.getPageSize();

                // ── Header band ──────────────────────────────────────────
                float bandHeight = 74;
                float bandTop = page.getHeight();
                cb.saveState();
                cb.setColorFill(PRIMARY);
                cb.rectangle(0, bandTop - bandHeight, page.getWidth(), bandHeight);
                cb.fill();
                cb.restoreState();

                // Logo mark (rounded square with a simple box glyph, matching the app sidebar)
                float logoX = 36, logoY = bandTop - 50, logoSize = 30;
                cb.saveState();
                cb.setColorFill(BaseColor.WHITE);
                cb.roundRectangle(logoX, logoY, logoSize, logoSize, 7);
                cb.fillStroke();
                cb.setColorStroke(PRIMARY);
                cb.setLineWidth(1.6f);
                cb.roundRectangle(logoX + 6, logoY + 6, logoSize - 12, logoSize - 12, 3);
                cb.stroke();
                cb.restoreState();

                try {
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                            new Phrase("StockSense", FONT_TITLE), logoX + logoSize + 10, bandTop - 30, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                            new Phrase("AI-Powered Inventory Management System", FONT_SUBTITLE),
                            logoX + logoSize + 10, bandTop - 44, 0);

                    Font reportTitleFont = new Font(Font.FontFamily.HELVETICA, 12.5f, Font.BOLD, BaseColor.WHITE);
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                            new Phrase(reportTitle, reportTitleFont), page.getWidth() - 36, bandTop - 26, 0);
                    Font metaFont = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, new BaseColor(0xe4, 0xdb, 0xfa));
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                            new Phrase(metaLine, metaFont), page.getWidth() - 36, bandTop - 42, 0);
                } catch (Exception ignored) { }

                // ── Footer band ──────────────────────────────────────────
                cb.saveState();
                cb.setColorStroke(BORDER);
                cb.setLineWidth(0.6f);
                cb.moveTo(36, 40);
                cb.lineTo(page.getWidth() - 36, 40);
                cb.stroke();
                cb.restoreState();

                try {
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                            new Phrase("StockSense — AI-Powered Inventory Management System", FONT_FOOTER), 36, 26, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                            new Phrase("Page " + writer.getPageNumber(), FONT_FOOTER), page.getWidth() - 36, 26, 0);
                } catch (Exception ignored) { }
            }
        };
    }

    public static String generatedAtLine() {
        return "Generated " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'at' h:mm a"));
    }

    public static Paragraph sectionTitle(String text) {
        Paragraph p = new Paragraph(text, FONT_SECTION);
        p.setSpacingBefore(10);
        p.setSpacingAfter(8);
        return p;
    }

    public static void addKpiCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(SURFACE_2);
        cell.setBorderColor(BORDER);
        cell.setPadding(10);
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", FONT_LABEL));
        p.add(new Chunk(value, FONT_VALUE));
        cell.addElement(p);
        table.addCell(cell);
    }

    public static void addHeaderCell(PdfPTable table, String text) {
        addHeaderCell(table, text, Element.ALIGN_LEFT);
    }

    public static void addHeaderCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_TABLE_HEAD));
        cell.setBackgroundColor(PRIMARY);
        cell.setPadding(6);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(BORDER);
        table.addCell(cell);
    }

    public static void addBodyCell(PdfPTable table, String text) {
        addBodyCell(table, text, Element.ALIGN_LEFT, false);
    }

    public static void addBodyCell(PdfPTable table, String text, int align) {
        addBodyCell(table, text, align, false);
    }

    public static void addBodyCell(PdfPTable table, String text, int align, boolean zebra) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", FONT_TABLE_CELL));
        cell.setPadding(6);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(BORDER);
        if (zebra) cell.setBackgroundColor(SURFACE_2);
        table.addCell(cell);
    }

    public static void addTotalCell(PdfPTable table, String text, int align, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_TABLE_TOTAL));
        cell.setColspan(colspan);
        cell.setPadding(7);
        cell.setBackgroundColor(SURFACE_2);
        cell.setBorderColor(BORDER);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        return "Rs " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
