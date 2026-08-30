package com.stocksense.service;

import com.stocksense.dto.request.InventoryAdjustRequest;
import com.stocksense.entity.*;
import com.stocksense.repository.*;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryLogRepository inventoryLogRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public InventoryLog adjustStock(InventoryAdjustRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        int qtyBefore = product.getQuantity();
        int qtyAfter;

        InventoryLog.MovementType type = InventoryLog.MovementType.valueOf(request.getMovementType());

        switch (type) {
            case STOCK_IN, RETURN, INVOICE_UPDATE -> qtyAfter = qtyBefore + request.getQuantity();
            case STOCK_OUT, DAMAGED -> {
                if (qtyBefore < request.getQuantity()) {
                    throw new RuntimeException("Insufficient stock. Available: " + qtyBefore);
                }
                qtyAfter = qtyBefore - request.getQuantity();
            }
            case ADJUSTMENT -> qtyAfter = request.getQuantity();
            default -> throw new RuntimeException("Unknown movement type");
        }

        product.setQuantity(qtyAfter);
        productRepository.save(product);

        InventoryLog log = new InventoryLog();
        log.setProduct(product);
        log.setMovementType(type);
        log.setQuantity(request.getQuantity());
        log.setQuantityBefore(qtyBefore);
        log.setQuantityAfter(qtyAfter);
        log.setReferenceNo(request.getReferenceNo());
        log.setNotes(request.getNotes());

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userRepository.findByUsername(username).ifPresent(log::setUser);

        InventoryLog saved = inventoryLogRepository.save(log);
        auditLogService.log("INVENTORY_" + type.name(), "Product", product.getId(),
                "Stock change: " + qtyBefore + " -> " + qtyAfter);

        return saved;
    }

    public Page<InventoryLog> findAllLogs(int page, int size) {
        return inventoryLogRepository.findAllOrderByCreatedAtDesc(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    public Page<InventoryLog> findLogsByProduct(Long productId, int page, int size) {
        return inventoryLogRepository.findByProductIdOrderByCreatedAtDesc(
                productId, PageRequest.of(page, size));
    }
}
