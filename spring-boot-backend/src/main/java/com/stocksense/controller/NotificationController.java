package com.stocksense.controller;

import com.stocksense.entity.Product;
import com.stocksense.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final ProductRepository productRepository;

    @GetMapping("/low-stock")
    public ResponseEntity<Map<String, Object>> getLowStockAlerts() {
        List<Product> lowStock = productRepository.findLowStockProducts();

        List<Map<String, Object>> alerts = new ArrayList<>();
        for (Product p : lowStock) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("id",           p.getId());
            alert.put("name",         p.getName());
            alert.put("sku",          p.getSku());
            alert.put("quantity",     p.getQuantity());
            alert.put("minLevel",     p.getMinStockLevel());
            alert.put("unit",         p.getUnit());
            alert.put("imagePath",    p.getImagePath());
            alert.put("critical",     p.getQuantity() == 0);
            alerts.add(alert);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count",  alerts.size());
        response.put("alerts", alerts);
        return ResponseEntity.ok(response);
    }
}
