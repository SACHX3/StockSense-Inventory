package com.stocksense.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksense.entity.*;
import com.stocksense.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIIntegrationService {

    @Value("${app.ai-service.base-url:http://localhost:8000}")
    private String aiServiceUrl;

    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final ForecastResultRepository forecastResultRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    // Normal calls (predict, OCR, health): fail fast so a hung AI service never
    // stalls a page load - the fallback forecast kicks in instead.
    private RestTemplate restTemplate = createRestTemplate(Duration.ofSeconds(15));

    // Retraining fits one Random Forest per product, so it is minutes-scale work,
    // not seconds-scale. Reusing the 15s template made every retrain report
    // "Read timed out" while FastAPI was still training happily in the background
    // and went on to answer 200 OK to a client that had already walked away.
    private RestTemplate retrainRestTemplate = createRestTemplate(Duration.ofMinutes(10));

    private static RestTemplate createRestTemplate(Duration readTimeout) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    // ============ FORECASTING ============

    @Transactional
    public Map<String, Object> getForecast(Long productId, int days) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("product_id", productId);
            requestBody.put("forecast_days", days);
            requestBody.put("sales_history", buildSalesData(productId));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = postJson(
                    aiServiceUrl + "/api/forecast/predict", entity);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                saveForecastResults(productId, response.getBody());
                return response.getBody();
            }
        } catch (Exception e) {
            log.warn("AI service unavailable, using fallback forecast: {}", e.getMessage());
            return generateFallbackForecast(productId, days);
        }
        return generateFallbackForecast(productId, days);
    }

    public Map<String, Object> retrainModel() {
        try {
            Map<String, Object> trainingData = buildAllSalesData();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(trainingData, headers);

            ResponseEntity<Map<String, Object>> response = postJson(
                    aiServiceUrl + "/api/forecast/retrain", entity, retrainRestTemplate);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("AI retrain timed out or could not be reached: {}", e.getMessage());
            return Map.of("status", "error",
                    "message", "Retraining is taking longer than expected. It may still be "
                             + "running in the AI service - check ai-service.log, then reload.");
        } catch (Exception e) {
            log.warn("AI retrain failed: {}", e.getMessage());
        }
        return Map.of("status", "error", "message", "AI service unavailable");
    }

    /** Check the external FastAPI service without affecting inventory data. */
    public Map<String, Object> checkServiceAvailability() {
        try {
            ResponseEntity<Map<String, Object>> response = getJson(aiServiceUrl + "/health");
            if (response.getStatusCode().is2xxSuccessful()) {
                return Map.of(
                        "available", true,
                        "service", "FastAPI AI/OCR",
                        "message", "FastAPI AI/OCR service is available");
            }
        } catch (Exception e) {
            log.warn("AI service health check failed: {}", e.getMessage());
        }
        return Map.of(
                "available", false,
                "service", "FastAPI AI/OCR",
                "message", "AI/OCR service unavailable. Core inventory remains available.");
    }

    /** Keeps the RestTemplate wire type as Map.class while exposing a typed response. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private ResponseEntity<Map<String, Object>> postJson(String url, HttpEntity<?> request) {
        return postJson(url, request, restTemplate);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ResponseEntity<Map<String, Object>> postJson(String url, HttpEntity<?> request,
                                                        RestTemplate template) {
        ResponseEntity<Map> response = template.postForEntity(url, request, Map.class);
        return new ResponseEntity<>((Map<String, Object>) response.getBody(),
                response.getHeaders(), response.getStatusCode());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ResponseEntity<Map<String, Object>> getJson(String url) {
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        return new ResponseEntity<>((Map<String, Object>) response.getBody(),
                response.getHeaders(), response.getStatusCode());
    }

    // ============ OCR ============

    public Map<String, Object> processInvoice(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        invoice.setOcrStatus(Invoice.OcrStatus.PROCESSING);
        invoiceRepository.save(invoice);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("invoice_id", invoiceId);
            requestBody.put("file_path", invoice.getFilePath());
            requestBody.put("file_type", invoice.getFileType().name());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response = postJson(
                    aiServiceUrl + "/api/ocr/process", entity);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                invoice.setRawOcrText((String) result.get("raw_text"));
                // Keep the OCR-detected business invoice number so duplicate uploads
                // can be blocked even when they have different database IDs.
                Object extractedInvoiceNumber = result.get("invoice_number");
                if (extractedInvoiceNumber == null) {
                    extractedInvoiceNumber = result.get("invoiceNumber");
                }
                if ((invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank())
                        && extractedInvoiceNumber != null
                        && !extractedInvoiceNumber.toString().isBlank()) {
                    invoice.setInvoiceNumber(extractedInvoiceNumber.toString().trim());
                }
                invoice.setExtractedData(objectMapper.writeValueAsString(result));
                invoice.setOcrStatus(Invoice.OcrStatus.COMPLETED);
                invoiceRepository.save(invoice);

                // Parse and save invoice items
                parseAndSaveInvoiceItems(invoice, result);
                return result;
            }
        } catch (Exception e) {
            log.warn("OCR service unavailable: {}", e.getMessage());
            invoice.setOcrStatus(Invoice.OcrStatus.FAILED);
            invoice.setNotes("OCR service unavailable: " + e.getMessage());
            invoiceRepository.save(invoice);
        }
        return Map.of("status", "error", "message", "OCR service unavailable");
    }

    @Transactional
    public Map<String, Object> applyInvoiceToInventory(Long invoiceId) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        String invoiceNumber = invoice.getInvoiceNumber() == null
                ? "" : invoice.getInvoiceNumber().trim();
        String invoiceLabel = invoiceNumber.isBlank() ? "(not provided)" : invoiceNumber;

        if (Boolean.TRUE.equals(invoice.getIsApplied())) {
            throw new RuntimeException("Duplicate OCR application blocked for invoice number "
                    + invoiceLabel + ". This invoice was already applied to inventory; "
                    + "no additional stock movement was created.");
        }

        if (!invoiceNumber.isBlank()
                && !invoiceRepository.findAppliedByInvoiceNumberForUpdate(invoiceNumber, invoiceId).isEmpty()) {
            throw new RuntimeException("Duplicate OCR application blocked for invoice number "
                    + invoiceLabel + ". This invoice number was already applied to inventory; "
                    + "no additional stock movement was created.");
        }

        int appliedCount = 0;
        int skippedCount = 0;
        java.util.List<String> errors = new java.util.ArrayList<>();

        for (InvoiceItem item : invoice.getItems()) {
            // Skip items with no quantity
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                skippedCount++;
                continue;
            }

            // Try to find product: first check direct link, then match by name
            com.stocksense.entity.Product product = item.getProduct();

            if (product == null && item.getProductName() != null) {
                // Try to match by product name (case-insensitive partial match)
                String searchName = item.getProductName().trim().toLowerCase();
                product = productRepository.findByIsActiveTrue().stream()
                        .filter(p -> {
                            String pName = p.getName().toLowerCase();
                            return pName.contains(searchName) || searchName.contains(pName)
                                   || pName.startsWith(searchName.substring(0, Math.min(5, searchName.length())));
                        })
                        .findFirst()
                        .orElse(null);

                if (product != null) {
                    item.setProduct(product);
                    item.setIsValidated(true);
                    invoiceItemRepository.save(item);
                    log.info("Matched '{}' to product '{}'", item.getProductName(), product.getName());
                }
            }

            if (product == null) {
                log.warn("No product match for: '{}' - skipping", item.getProductName());
                errors.add("No match: " + item.getProductName());
                skippedCount++;
                continue;
            }

            // Apply stock update
            try {
                com.stocksense.dto.request.InventoryAdjustRequest req = new com.stocksense.dto.request.InventoryAdjustRequest();
                req.setProductId(product.getId());
                req.setMovementType("INVOICE_UPDATE");
                req.setQuantity(item.getQuantity());
                req.setReferenceNo("OCR-INV-" + invoiceId);
                req.setNotes("Applied from OCR invoice #" + invoiceId + " - " + item.getProductName());
                inventoryService.adjustStock(req);

                item.setIsValidated(true);
                invoiceItemRepository.save(item);
                appliedCount++;
                log.info("Applied: {} qty={} to product '{}'", item.getProductName(), item.getQuantity(), product.getName());
            } catch (Exception e) {
                log.error("Failed to apply item '{}': {}", item.getProductName(), e.getMessage());
                errors.add("Error for " + item.getProductName() + ": " + e.getMessage());
                skippedCount++;
            }
        }

        invoice.setIsApplied(true);
        invoice.setAppliedAt(LocalDateTime.now());
        invoiceRepository.save(invoice);

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("status", "success");
        result.put("applied_items", appliedCount);
        result.put("skipped_items", skippedCount);
        result.put("errors", errors);
        result.put("message", appliedCount + " item(s) applied to inventory" +
                (skippedCount > 0 ? ", " + skippedCount + " skipped (no product match)" : ""));
        return result;
    }

    // ============ HELPERS ============

    private List<Map<String, Object>> buildSalesData(Long productId) {
        return buildSalesData(productId, 6);
    }

    private List<Map<String, Object>> buildSalesData(Long productId, int months) {
        // Real day-by-day sold quantity for this product, which is what the FastAPI
        // Random Forest model actually needs to train on. Previously this sent a single
        // placeholder {"product_id": X, "period_days": 180} entry, which meant the AI
        // service never had enough real records (needs > 5) to use the ML model and
        // always fell back to a synthetic, product-id-seeded forecast.
        LocalDateTime start = LocalDateTime.now().minusMonths(months);
        List<Object[]> rows = saleItemRepository.findDailySalesForProduct(productId, start);

        Map<LocalDate, Integer> qtyByDate = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate date = toLocalDate(row[0]);
            if (date != null) {
                int qty = row[1] != null ? ((Number) row[1]).intValue() : 0;
                qtyByDate.put(date, qty);
            }
        }

        // Zero-fill every day in the window (not just days with sales) so each list
        // entry corresponds to one real calendar day in sequence - the model derives
        // day-of-week/seasonal features from the entry's position in the list.
        List<Map<String, Object>> data = new ArrayList<>();
        LocalDate cursor = start.toLocalDate();
        LocalDate today = LocalDate.now();
        while (!cursor.isAfter(today)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("date", cursor.toString());
            entry.put("quantity", qtyByDate.getOrDefault(cursor, 0));
            data.add(entry);
            cursor = cursor.plusDays(1);
        }
        return data;
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        try {
            return LocalDate.parse(value.toString().substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> buildAllSalesData() {
        // One grouped query for the whole catalogue instead of one per product.
        // The previous version called buildSalesData(productId) in a loop, which
        // issued a separate findDailySalesForProduct query for each of ~50 products
        // before the retrain request was even sent.
        LocalDateTime start = LocalDateTime.now().minusMonths(12);
        List<Object[]> rows = saleItemRepository.findDailySalesForAllProducts(start);

        // product id -> (date -> qty), preserving insertion order for stable output
        Map<Long, Map<LocalDate, Integer>> byProduct = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) continue;
            Long productId = ((Number) row[0]).longValue();
            LocalDate date = toLocalDate(row[1]);
            if (date == null) continue;
            int qty = row[2] != null ? ((Number) row[2]).intValue() : 0;
            byProduct.computeIfAbsent(productId, k -> new LinkedHashMap<>()).put(date, qty);
        }

        Map<String, Object> products = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (Map.Entry<Long, Map<LocalDate, Integer>> e : byProduct.entrySet()) {
            Map<LocalDate, Integer> qtyByDate = e.getValue();
            if (qtyByDate.isEmpty()) continue;

            // Fill the gaps: a day with no sale is a real zero, and dropping it would
            // teach the model that demand is never zero.
            LocalDate cursor = qtyByDate.keySet().stream().min(LocalDate::compareTo).orElse(today);
            List<Map<String, Object>> series = new ArrayList<>();
            while (!cursor.isAfter(today)) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("date", cursor.toString());
                entry.put("quantity", qtyByDate.getOrDefault(cursor, 0));
                series.add(entry);
                cursor = cursor.plusDays(1);
            }
            products.put(String.valueOf(e.getKey()), series);
        }

        Map<String, Object> salesHistory = new HashMap<>();
        salesHistory.put("products", products);
        salesHistory.put("product_count", products.size());
        salesHistory.put("period", "12 months");

        Map<String, Object> data = new HashMap<>();
        data.put("sales_history", salesHistory);
        data.put("period", "12 months");
        return data;
    }

    @SuppressWarnings("unchecked")
    private void parseAndSaveInvoiceItems(Invoice invoice, Map<String, Object> result) {
        try {
            Object itemsObj = result.get("items");
            if (itemsObj instanceof List<?> itemsList) {
                // Re-processing an invoice replaces its extracted items, it does not add
                // to them. Without this the "Re-process OCR" button appended a second
                // (then third...) copy of every line, and the totals doubled with it.
                //
                // Guarded on isApplied: once an invoice has been applied to inventory its
                // items are the audit trail of what moved stock, so they must never be
                // deleted out from under it.
                if (Boolean.TRUE.equals(invoice.getIsApplied())) {
                    log.warn("Invoice {} is already applied - keeping existing items, skipping re-parse",
                            invoice.getId());
                    return;
                }
                invoiceItemRepository.deleteByInvoiceId(invoice.getId());
                invoiceItemRepository.flush();
                for (Object itemObj : itemsList) {
                    if (itemObj instanceof Map<?, ?> itemMap) {
                        InvoiceItem item = new InvoiceItem();
                        item.setInvoice(invoice);
                        item.setProductName(itemMap.get("product_name") != null ? itemMap.get("product_name").toString() : "Unknown");
                        if (itemMap.get("quantity") != null) item.setQuantity(((Number) itemMap.get("quantity")).intValue());
                        if (itemMap.get("unit_price") != null) item.setUnitPrice(new BigDecimal(itemMap.get("unit_price").toString()));
                        if (itemMap.get("total_price") != null) item.setTotalPrice(new BigDecimal(itemMap.get("total_price").toString()));
                        if (itemMap.get("confidence") != null) item.setConfidenceScore(new BigDecimal(itemMap.get("confidence").toString()));

                        // Try to match product by name
                        String productName = item.getProductName();
                        if (productName != null) {
                            productRepository.findByIsActiveTrue().stream()
                                    .filter(p -> p.getName().toLowerCase().contains(productName.toLowerCase()) ||
                                            productName.toLowerCase().contains(p.getName().toLowerCase()))
                                    .findFirst()
                                    .ifPresent(item::setProduct);
                        }

                        invoiceItemRepository.save(item);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing invoice items: {}", e.getMessage());
        }
    }

    private Map<String, Object> generateFallbackForecast(Long productId, int days) {
        // Simple moving average fallback
        Map<String, Object> result = new HashMap<>();
        result.put("status", "fallback");
        result.put("service_available", false);
        result.put("message", "AI service unavailable. Fallback forecast displayed; core inventory remains available.");
        result.put("product_id", productId);
        result.put("forecast_days", days);
        result.put("model", "fallback_average");

        List<Map<String, Object>> predictions = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // Get recent sales average
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        List<Object[]> recentSales = saleItemRepository.findProductSalesHistory(start);
        int avgDaily = 2; // default
        for (Object[] row : recentSales) {
            if (row[0] != null && ((Number) row[0]).longValue() == productId) {
                avgDaily = (int) Math.max(1, ((Number) row[1]).doubleValue() / 30);
                break;
            }
        }

        for (int i = 1; i <= days; i++) {
            Map<String, Object> pred = new HashMap<>();
            pred.put("date", today.plusDays(i).toString());
            pred.put("predicted_demand", avgDaily);
            pred.put("confidence_lower", Math.max(0, avgDaily - 1));
            pred.put("confidence_upper", avgDaily + 2);
            predictions.add(pred);
        }

        result.put("predictions", predictions);
        result.put("mae", 1.5);
        result.put("rmse", 2.0);

        // Save to DB
        saveForecastResults(productId, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    private void saveForecastResults(Long productId, Map<String, Object> result) {
        try {
            forecastResultRepository.deleteByProductId(productId);
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) return;

            Object predsObj = result.get("predictions");
            if (predsObj instanceof List<?> preds) {
                for (Object predObj : preds) {
                    if (predObj instanceof Map<?, ?> pred) {
                        ForecastResult fr = new ForecastResult();
                        fr.setProduct(product);
                        if (pred.get("date") != null) fr.setForecastDate(LocalDate.parse(pred.get("date").toString()));
                        if (pred.get("predicted_demand") != null) fr.setPredictedDemand(((Number) pred.get("predicted_demand")).intValue());
                        if (pred.get("predicted_demand_exact") != null) {
                            fr.setPredictedDemandExact(new BigDecimal(pred.get("predicted_demand_exact").toString()));
                        } else if (pred.get("predicted_demand") != null) {
                            fr.setPredictedDemandExact(new BigDecimal(pred.get("predicted_demand").toString()));
                        }
                        if (pred.get("confidence_lower") != null) fr.setConfidenceLower(((Number) pred.get("confidence_lower")).intValue());
                        if (pred.get("confidence_upper") != null) fr.setConfidenceUpper(((Number) pred.get("confidence_upper")).intValue());
                        if (result.get("model") != null) fr.setModelVersion(result.get("model").toString());
                        if (result.get("mae") != null) fr.setMae(new BigDecimal(result.get("mae").toString()));
                        if (result.get("rmse") != null) fr.setRmse(new BigDecimal(result.get("rmse").toString()));
                        forecastResultRepository.save(fr);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error saving forecast results: {}", e.getMessage());
        }
    }
}
