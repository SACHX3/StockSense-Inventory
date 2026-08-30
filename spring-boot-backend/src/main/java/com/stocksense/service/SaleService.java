package com.stocksense.service;

import com.stocksense.dto.request.SaleRequest;
import com.stocksense.entity.*;
import com.stocksense.repository.*;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InventoryLogRepository inventoryLogRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public Sale createSale(SaleRequest request) {
        Sale sale = new Sale();
        sale.setInvoiceNumber(generateInvoiceNumber());
        sale.setCustomerName(request.getCustomerName());
        sale.setCustomerPhone(request.getCustomerPhone());
        sale.setPaymentMethod(Sale.PaymentMethod.valueOf(request.getPaymentMethod()));
        sale.setNotes(request.getNotes());

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userRepository.findByUsername(username).ifPresent(sale::setUser);

        BigDecimal subtotal = BigDecimal.ZERO;
        List<SaleItem> items = new ArrayList<>();

        for (SaleRequest.SaleItemRequest itemReq : request.getItems()) {
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new RuntimeException("Quantity must be greater than zero");
            }
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + itemReq.getProductId()));

            if (product.getQuantity() < itemReq.getQuantity()) {
                throw new RuntimeException("Insufficient stock for: " + product.getName()
                        + ". Requested: " + itemReq.getQuantity()
                        + ", available: " + product.getQuantity());
            }

            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setProduct(product);
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(itemReq.getUnitPrice());
            item.setDiscountPercent(itemReq.getDiscountPercent());

            BigDecimal itemTotal = itemReq.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            if (itemReq.getDiscountPercent() != null && itemReq.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal discountAmt = itemTotal.multiply(itemReq.getDiscountPercent()).divide(BigDecimal.valueOf(100));
                itemTotal = itemTotal.subtract(discountAmt);
            }
            item.setTotalPrice(itemTotal);
            subtotal = subtotal.add(itemTotal);
            items.add(item);

            // Deduct stock
            int qtyBefore = product.getQuantity();
            product.setQuantity(qtyBefore - itemReq.getQuantity());
            productRepository.save(product);

            // Log inventory movement
            InventoryLog log = new InventoryLog();
            log.setProduct(product);
            log.setMovementType(InventoryLog.MovementType.STOCK_OUT);
            log.setQuantity(itemReq.getQuantity());
            log.setQuantityBefore(qtyBefore);
            log.setQuantityAfter(product.getQuantity());
            log.setReferenceNo(sale.getInvoiceNumber());
            log.setNotes("Sale: " + sale.getInvoiceNumber());
            userRepository.findByUsername(username).ifPresent(log::setUser);
            inventoryLogRepository.save(log);
        }

        BigDecimal discount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal total = subtotal.subtract(discount);

        sale.setSubtotal(subtotal);
        sale.setDiscountAmount(discount);
        sale.setTotalAmount(total);
        sale.setPaymentStatus(Sale.PaymentStatus.PAID);
        sale.setItems(items);

        Sale saved = saleRepository.save(sale);
        auditLogService.log("SALE_CREATED", "Sale", saved.getId(), "Invoice: " + saved.getInvoiceNumber());
        return saved;
    }

    public Page<Sale> findAll(int page, int size) {
        return saleRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public List<Sale> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return saleRepository.findByDateRange(start, end);
    }

    public Sale findById(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
    }

    public BigDecimal getTodayRevenue() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = LocalDate.now().atTime(23, 59, 59);
        return saleRepository.sumRevenueByDateRange(start, end);
    }

    public BigDecimal getMonthlyRevenue() {
        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = LocalDateTime.now();
        return saleRepository.sumRevenueByDateRange(start, end);
    }

    public long getTodaySalesCount() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = LocalDate.now().atTime(23, 59, 59);
        return saleRepository.countByDateRange(start, end);
    }

    public List<Object[]> getMonthlyRevenueData() {
        LocalDateTime start = LocalDateTime.now().minusMonths(12);
        return saleRepository.getMonthlyRevenue(start);
    }

    public List<Object[]> getMonthlyRevenueData(LocalDateTime start) {
        return saleRepository.getMonthlyRevenue(start);
    }

    public List<Object[]> getDailySalesData() {
        LocalDateTime start = LocalDateTime.now().minusDays(29).toLocalDate().atStartOfDay();
        LocalDateTime end   = LocalDateTime.now();
        return saleRepository.getDailyRevenue(start, end);
    }

    private String generateInvoiceNumber() {
        String prefix = "INV-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";
        long count = saleRepository.count() + 1;
        // saleRepository.count() alone is a race condition: two concurrent checkouts can
        // read the same count before either commits and generate the exact same invoice
        // number, tripping the sales.invoice_number UNIQUE constraint for one of them.
        // A short random suffix keeps the number human-readable/roughly sequential while
        // making an exact collision between simultaneous checkouts extremely unlikely.
        String randomSuffix = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return prefix + String.format("%04d", count) + "-" + randomSuffix;
    }
}
