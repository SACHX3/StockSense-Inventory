package com.stocksense.controller;

import com.stocksense.dto.request.SupplierRequest;
import com.stocksense.dto.response.ApiResponse;
import com.stocksense.entity.Product;
import com.stocksense.entity.Supplier;
import com.stocksense.repository.ProductRepository;
import com.stocksense.service.SupplierService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Controller
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;
    private final ProductRepository productRepository;

    @GetMapping
    public String list(Model model, @RequestParam(required = false) String keyword) {
        List<Supplier> suppliers = keyword != null && !keyword.isEmpty()
                ? supplierService.search(keyword)
                : supplierService.findAll();
        model.addAttribute("suppliers", suppliers);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageTitle", "Suppliers");
        return "suppliers/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("supplier", new SupplierRequest());
        model.addAttribute("pageTitle", "Add Supplier");
        return "suppliers/form";
    }

    @PostMapping("/create")
    public String create(@Valid @ModelAttribute SupplierRequest request,
                         RedirectAttributes redirectAttributes) {
        try {
            supplierService.create(request);
            redirectAttributes.addFlashAttribute("successMsg", "Supplier created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/suppliers/create";
        }
        return "redirect:/suppliers";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        Supplier supplier = supplierService.findById(id);
        SupplierRequest req = new SupplierRequest();
        req.setName(supplier.getName());
        req.setContactPerson(supplier.getContactPerson());
        req.setEmail(supplier.getEmail());
        req.setPhone(supplier.getPhone());
        req.setAddress(supplier.getAddress());
        req.setCity(supplier.getCity());
        req.setCountry(supplier.getCountry());
        req.setTaxNumber(supplier.getTaxNumber());
        req.setPaymentTerms(supplier.getPaymentTerms());
        req.setNotes(supplier.getNotes());
        model.addAttribute("supplier", req);
        model.addAttribute("supplierId", id);
        model.addAttribute("pageTitle", "Edit Supplier");
        return "suppliers/form";
    }

    @PostMapping("/edit/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute SupplierRequest request,
                         RedirectAttributes redirectAttributes) {
        try {
            supplierService.update(id, request);
            redirectAttributes.addFlashAttribute("successMsg", "Supplier updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/suppliers";
    }

    @GetMapping("/view/{id}")
    public String view(@PathVariable Long id, Model model) {
        Supplier supplier = supplierService.findById(id);
        List<Product> products = productRepository.findBySupplierIdAndIsActiveTrueOrderByNameAsc(id);
        long totalActiveProducts = productRepository.findByIsActiveTrue().size();
        long linkedProductCount = products.size();
        long totalUnits = products.stream()
                .mapToLong(product -> product.getQuantity() == null ? 0 : product.getQuantity())
                .sum();
        BigDecimal stockValue = products.stream()
                .map(product -> {
                    BigDecimal buyingPrice = product.getBuyingPrice() == null
                            ? BigDecimal.ZERO : product.getBuyingPrice();
                    int quantity = product.getQuantity() == null ? 0 : product.getQuantity();
                    return buyingPrice.multiply(BigDecimal.valueOf(quantity));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal coveragePercent = totalActiveProducts == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(linkedProductCount * 100.0 / totalActiveProducts)
                    .setScale(1, RoundingMode.HALF_UP);

        model.addAttribute("supplier", supplier);
        model.addAttribute("products", products);
        model.addAttribute("totalActiveProducts", totalActiveProducts);
        model.addAttribute("linkedProductCount", linkedProductCount);
        model.addAttribute("totalUnits", totalUnits);
        model.addAttribute("stockValue", stockValue);
        model.addAttribute("coveragePercent", coveragePercent);
        model.addAttribute("pageTitle", "Supplier Details");
        return "suppliers/view";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            supplierService.delete(id);
            redirectAttributes.addFlashAttribute("successMsg", "Supplier deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/suppliers";
    }

    @GetMapping("/api/all")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Supplier>>> getAllApi() {
        return ResponseEntity.ok(ApiResponse.success("OK", supplierService.findAllActive()));
    }

    @GetMapping("/api/search")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Supplier>>> searchApi(@RequestParam String keyword) {
        List<Supplier> results = supplierService.search(keyword);
        if (results.size() > 20) {
            results = results.subList(0, 20);
        }
        return ResponseEntity.ok(ApiResponse.success("OK", results));
    }
}
