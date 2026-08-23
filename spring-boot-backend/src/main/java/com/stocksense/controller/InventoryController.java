package com.stocksense.controller;

import com.stocksense.dto.request.InventoryAdjustRequest;
import com.stocksense.dto.response.ApiResponse;
import com.stocksense.entity.InventoryLog;
import com.stocksense.service.InventoryService;
import com.stocksense.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final ProductService productService;

    @GetMapping
    public String list(Model model,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size) {
        Page<InventoryLog> logs = inventoryService.findAllLogs(page, size);
        model.addAttribute("logs", logs);
        model.addAttribute("pageTitle", "Inventory Logs");
        return "inventory/logs";
    }

    @GetMapping("/adjust")
    public String adjustForm(Model model) {
        model.addAttribute("products", productService.findAllActive());
        model.addAttribute("pageTitle", "Stock Adjustment");
        return "inventory/adjust";
    }

    @PostMapping("/adjust")
    public String adjust(@Valid @ModelAttribute InventoryAdjustRequest request,
                         RedirectAttributes redirectAttributes) {
        try {
            inventoryService.adjustStock(request);
            redirectAttributes.addFlashAttribute("successMsg", "Stock adjusted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/inventory";
    }

    @PostMapping("/api/adjust")
    @ResponseBody
    public ResponseEntity<ApiResponse<InventoryLog>> adjustApi(@Valid @RequestBody InventoryAdjustRequest request) {
        try {
            InventoryLog log = inventoryService.adjustStock(request);
            return ResponseEntity.ok(ApiResponse.success("Stock adjusted successfully", log));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/product/{productId}")
    public String productLogs(@PathVariable Long productId, Model model,
                              @RequestParam(defaultValue = "0") int page) {
        model.addAttribute("logs", inventoryService.findLogsByProduct(productId, page, 20));
        model.addAttribute("product", productService.findById(productId));
        model.addAttribute("pageTitle", "Product Inventory History");
        return "inventory/product-logs";
    }
}
