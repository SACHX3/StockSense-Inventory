package com.stocksense.controller;

import com.stocksense.entity.Invoice;
import com.stocksense.repository.InvoiceRepository;
import com.stocksense.service.ProductService;
import com.stocksense.service.SupplierService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/evidence")
@RequiredArgsConstructor
public class EvidenceController {

    private final SupplierService supplierService;
    private final ProductService productService;
    private final InvoiceRepository invoiceRepository;

    @GetMapping
    public String index(Model model) {
        List<Invoice> appliedInvoices = invoiceRepository.findAll().stream()
                .filter(invoice -> Boolean.TRUE.equals(invoice.getIsApplied()))
                .toList();
        model.addAttribute("suppliers", supplierService.findAllActive());
        model.addAttribute("products", productService.findAllActive());
        model.addAttribute("appliedInvoices", appliedInvoices);
        model.addAttribute("pageTitle", "Evidence Demonstrations");
        return "evidence/index";
    }

    @GetMapping("/supplier/{id}")
    public String supplierDetails(@PathVariable Long id) {
        return "redirect:/suppliers/view/" + id;
    }

    @GetMapping("/product-history/{id}")
    public String productHistory(@PathVariable Long id) {
        return "redirect:/inventory/product/" + id;
    }

    @GetMapping("/insufficient-stock")
    public String insufficientStock() {
        return "redirect:/sales/create";
    }

    @GetMapping("/duplicate-ocr/{id}")
    public String duplicateOcr(@PathVariable Long id) {
        return "redirect:/ocr/view/" + id;
    }

    @GetMapping("/ai-service")
    public String aiService() {
        return "redirect:/forecasting";
    }
}
