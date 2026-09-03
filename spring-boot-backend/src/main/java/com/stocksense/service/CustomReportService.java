package com.stocksense.service;

import com.stocksense.entity.Product;
import com.stocksense.entity.Sale;
import com.stocksense.entity.Supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs the Custom Report Builder (/reports/custom). Lets the user pick a
 * real data source (Sales, Inventory, Suppliers), choose which real columns
 * to include, optionally group rows, and sort — then hands back a generic
 * (title, headers, rows) result that both the PDF and CSV exporters consume.
 * Every column maps to a real entity field; nothing here is fabricated.
 */
@Service
@RequiredArgsConstructor
public class CustomReportService {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final SaleService saleService;
    private final ProductService productService;
    private final SupplierService supplierService;

    public record CustomReportResult(String title, List<String> headers, List<List<String>> rows) { }

    // Columns available per source, in a stable order. Keys are what the
    // builder UI sends back in ?columns=... — every key maps to a real field.
    public static final Map<String, String> SALES_COLUMNS = new LinkedHashMap<>();
    public static final Map<String, String> INVENTORY_COLUMNS = new LinkedHashMap<>();
    public static final Map<String, String> SUPPLIER_COLUMNS = new LinkedHashMap<>();
    static {
        SALES_COLUMNS.put("invoice", "Invoice");
        SALES_COLUMNS.put("customer", "Customer");
        SALES_COLUMNS.put("subtotal", "Subtotal");
        SALES_COLUMNS.put("discount", "Discount");
        SALES_COLUMNS.put("total", "Total");
        SALES_COLUMNS.put("paymentMethod", "Payment Method");
        SALES_COLUMNS.put("paymentStatus", "Payment Status");
        SALES_COLUMNS.put("cashier", "Cashier");
        SALES_COLUMNS.put("date", "Date");

        INVENTORY_COLUMNS.put("name", "Product");
        INVENTORY_COLUMNS.put("sku", "SKU");
        INVENTORY_COLUMNS.put("category", "Category");
        INVENTORY_COLUMNS.put("supplier", "Supplier");
        INVENTORY_COLUMNS.put("stock", "Stock");
        INVENTORY_COLUMNS.put("buyPrice", "Buy Price");
        INVENTORY_COLUMNS.put("sellPrice", "Sell Price");
        INVENTORY_COLUMNS.put("stockValue", "Stock Value");

        SUPPLIER_COLUMNS.put("name", "Supplier");
        SUPPLIER_COLUMNS.put("city", "City");
        SUPPLIER_COLUMNS.put("phone", "Phone");
        SUPPLIER_COLUMNS.put("active", "Active");
        SUPPLIER_COLUMNS.put("paymentTerms", "Payment Terms");
    }

    /**
     * Keep only the columns that actually belong to the chosen source, in the
     * canonical order of that source's map.
     *
     * The builder UI keeps all three column groups in the DOM and merely hides the
     * two that do not apply, so a Sales report used to arrive with the Inventory
     * and Supplier checkboxes attached as well. Those keys hit `default -> ""` in
     * the row switch and `getOrDefault(c, c)` in the header, producing exactly the
     * reported symptom: extra columns headed with a raw key like "stockValue" and
     * completely empty underneath.
     *
     * Ordering by the map (not by request order) also stops a hand-edited URL from
     * producing duplicate or shuffled columns.
     */
    private List<String> sanitize(List<String> requested, Map<String, String> allowed) {
        if (requested == null || requested.isEmpty()) {
            return new ArrayList<>(allowed.keySet());
        }
        List<String> cols = new ArrayList<>();
        for (String key : allowed.keySet()) {
            if (requested.contains(key)) cols.add(key);
        }
        // Every requested column was foreign to this source - fall back to the
        // full set rather than handing back a report with no columns at all.
        return cols.isEmpty() ? new ArrayList<>(allowed.keySet()) : cols;
    }

    public CustomReportResult build(String source, LocalDate from, LocalDate to,
                                     List<String> columns, String groupBy, String sort) {
        return switch (source == null ? "" : source.toLowerCase()) {
            case "inventory" -> buildInventory(columns);
            case "suppliers" -> buildSuppliers(columns);
            default -> buildSales(from, to, columns, groupBy, sort);
        };
    }

    private CustomReportResult buildSales(LocalDate from, LocalDate to, List<String> columns, String groupBy, String sort) {
        List<String> cols = sanitize(columns, SALES_COLUMNS);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(23, 59, 59);
        List<Sale> sales = new ArrayList<>(saleService.findByDateRange(start, end));

        sales.sort(switch (sort == null ? "" : sort) {
            case "total_desc" -> Comparator.comparing((Sale s) -> nz(s.getTotalAmount())).reversed();
            case "customer_asc" -> Comparator.comparing(s -> s.getCustomerName() != null ? s.getCustomerName() : "Walk-in Customer");
            default -> Comparator.comparing(Sale::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        });

        if ("cashier".equals(groupBy)) {
            sales.sort(Comparator.comparing((Sale s) -> nzs(s.getCashierName()))
                    .thenComparing(Sale::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        } else if ("paymentMethod".equals(groupBy)) {
            sales.sort(Comparator.comparing((Sale s) -> s.getPaymentMethod() != null ? s.getPaymentMethod().name() : ""));
        } else if ("day".equals(groupBy)) {
            sales.sort(Comparator.comparing((Sale s) -> s.getCreatedAt() != null ? s.getCreatedAt().toLocalDate() : LocalDate.MIN));
        }

        List<String> headers = cols.stream().map(c -> SALES_COLUMNS.getOrDefault(c, c)).toList();
        List<List<String>> rows = new ArrayList<>();
        for (Sale s : sales) {
            List<String> row = new ArrayList<>();
            for (String c : cols) {
                row.add(switch (c) {
                    case "invoice" -> nzs(s.getInvoiceNumber());
                    case "customer" -> s.getCustomerName() != null ? s.getCustomerName() : "Walk-in Customer";
                    case "subtotal" -> money(s.getSubtotal());
                    case "discount" -> money(s.getDiscountAmount());
                    case "total" -> money(s.getTotalAmount());
                    case "paymentMethod" -> s.getPaymentMethod() != null ? s.getPaymentMethod().name() : "";
                    case "paymentStatus" -> s.getPaymentStatus() != null ? s.getPaymentStatus().name() : "";
                    case "cashier" -> nzs(s.getCashierName());
                    case "date" -> s.getCreatedAt() != null ? s.getCreatedAt().format(DATETIME_FMT) : "";
                    default -> "";
                });
            }
            rows.add(row);
        }
        return new CustomReportResult("Custom Sales Report", headers, rows);
    }

    private CustomReportResult buildInventory(List<String> columns) {
        List<String> cols = sanitize(columns, INVENTORY_COLUMNS);
        List<Product> products = productService.findAllActive();

        List<String> headers = cols.stream().map(c -> INVENTORY_COLUMNS.getOrDefault(c, c)).toList();
        List<List<String>> rows = new ArrayList<>();
        for (Product p : products) {
            BigDecimal buy = p.getBuyingPrice() != null ? p.getBuyingPrice() : BigDecimal.ZERO;
            int qty = p.getQuantity() != null ? p.getQuantity() : 0;
            BigDecimal stockValue = buy.multiply(BigDecimal.valueOf(qty));
            List<String> row = new ArrayList<>();
            for (String c : cols) {
                row.add(switch (c) {
                    case "name" -> nzs(p.getName());
                    case "sku" -> nzs(p.getSku());
                    case "category" -> p.getCategory() != null ? nzs(p.getCategory().getName()) : "";
                    case "supplier" -> p.getSupplier() != null ? nzs(p.getSupplier().getName()) : "";
                    case "stock" -> qty + " " + (p.getUnit() != null ? p.getUnit() : "");
                    case "buyPrice" -> money(buy);
                    case "sellPrice" -> money(p.getSellingPrice());
                    case "stockValue" -> money(stockValue);
                    default -> "";
                });
            }
            rows.add(row);
        }
        return new CustomReportResult("Custom Inventory Report", headers, rows);
    }

    private CustomReportResult buildSuppliers(List<String> columns) {
        List<String> cols = sanitize(columns, SUPPLIER_COLUMNS);
        List<Supplier> suppliers = supplierService.findAll();

        List<String> headers = cols.stream().map(c -> SUPPLIER_COLUMNS.getOrDefault(c, c)).toList();
        List<List<String>> rows = new ArrayList<>();
        for (Supplier s : suppliers) {
            List<String> row = new ArrayList<>();
            for (String c : cols) {
                row.add(switch (c) {
                    case "name" -> nzs(s.getName());
                    case "city" -> nzs(s.getCity());
                    case "phone" -> nzs(s.getPhone());
                    case "active" -> Boolean.TRUE.equals(s.getIsActive()) ? "Yes" : "No";
                    case "paymentTerms" -> nzs(s.getPaymentTerms());
                    default -> "";
                });
            }
            rows.add(row);
        }
        return new CustomReportResult("Custom Supplier Report", headers, rows);
    }

    private String nzs(String s) { return s != null ? s : ""; }
    private BigDecimal nz(BigDecimal b) { return b != null ? b : BigDecimal.ZERO; }
    private String money(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
