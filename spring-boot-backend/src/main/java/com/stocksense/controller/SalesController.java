package com.stocksense.controller;

import com.stocksense.dto.request.SaleRequest;
import com.stocksense.dto.response.ApiResponse;
import com.stocksense.entity.Sale;
import com.stocksense.service.ProductService;
import com.stocksense.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SaleService saleService;
    private final ProductService productService;

    @GetMapping
    public String list(Model model, @RequestParam(defaultValue = "0") int page) {
        Page<Sale> sales = saleService.findAll(page, 20);
        model.addAttribute("sales", sales);
        model.addAttribute("pageTitle", "Sales");
        return "sales/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("products", productService.findAllActive());
        model.addAttribute("pageTitle", "New Sale (POS)");
        return "sales/pos";
    }

    /**
     * Process sale - returns FLAT JSON response (no entity recursion)
     */
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Object>>> createSale(
            @Valid @RequestBody SaleRequest request) {
        try {
            Sale sale = saleService.createSale(request);

            // Build a flat response map - NO nested entities that cause recursion
            Map<String, Object> saleData = new HashMap<>();
            saleData.put("id",            sale.getId());
            saleData.put("invoiceNumber", sale.getInvoiceNumber());
            saleData.put("customerName",  sale.getCustomerName());
            saleData.put("customerPhone", sale.getCustomerPhone());
            saleData.put("subtotal",      sale.getSubtotal());
            saleData.put("discount",      sale.getDiscountAmount());
            saleData.put("total",         sale.getTotalAmount());
            saleData.put("paymentMethod", sale.getPaymentMethod().name());
            saleData.put("paymentStatus", sale.getPaymentStatus().name());
            saleData.put("itemCount",     sale.getItems().size());
            saleData.put("createdAt",     sale.getCreatedAt().toString());

            return ResponseEntity.ok(ApiResponse.success("Sale created successfully", saleData));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/view/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("sale", saleService.findById(id));
        model.addAttribute("pageTitle", "Sale Details");
        return "sales/view";
    }

    @GetMapping("/receipt/{id}")
    public String receipt(@PathVariable Long id, Model model) {
        model.addAttribute("sale", saleService.findById(id));
        return "sales/receipt";
    }
}
