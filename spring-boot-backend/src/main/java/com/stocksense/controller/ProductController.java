package com.stocksense.controller;

import com.stocksense.dto.request.ProductRequest;
import com.stocksense.dto.response.ApiResponse;
import com.stocksense.entity.Product;
import com.stocksense.service.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final SupplierService supplierService;

    @GetMapping
    public String list(Model model,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "15") int size,
                       @RequestParam(required = false) String keyword) {
        Page<Product> products = productService.findAll(page, size, keyword);
        model.addAttribute("products", products);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageTitle", "Products");
        model.addAttribute("lowStockCount", productService.countLowStock());
        return "products/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("product", new ProductRequest());
        model.addAttribute("categories", categoryService.findAllActive());
        model.addAttribute("suppliers", supplierService.findAllActive());
        model.addAttribute("pageTitle", "Add Product");
        return "products/form";
    }

    @PostMapping("/create")
    public String create(@Valid @ModelAttribute ProductRequest request,
                         @RequestParam(required = false) MultipartFile imageFile,
                         RedirectAttributes redirectAttributes) {
        try {
            productService.create(request, imageFile);
            redirectAttributes.addFlashAttribute("successMsg", "Product created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/products/create";
        }
        return "redirect:/products";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        Product product = productService.findById(id);
        // Map product to request
        ProductRequest req = new ProductRequest();
        req.setName(product.getName());
        req.setSku(product.getSku());
        req.setBarcode(product.getBarcode());
        req.setDescription(product.getDescription());
        req.setCategoryId(product.getCategory().getId());
        if (product.getSupplier() != null) req.setSupplierId(product.getSupplier().getId());
        req.setUnit(product.getUnit());
        req.setBuyingPrice(product.getBuyingPrice());
        req.setSellingPrice(product.getSellingPrice());
        req.setQuantity(product.getQuantity());
        req.setMinStockLevel(product.getMinStockLevel());
        req.setMaxStockLevel(product.getMaxStockLevel());

        model.addAttribute("product", req);
        model.addAttribute("productId", id);
        model.addAttribute("existingImage", product.getImagePath());
        model.addAttribute("categories", categoryService.findAllActive());
        model.addAttribute("suppliers", supplierService.findAllActive());
        model.addAttribute("pageTitle", "Edit Product");
        return "products/form";
    }

    @PostMapping("/edit/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute ProductRequest request,
                         @RequestParam(required = false) MultipartFile imageFile,
                         @RequestParam(value = "removeImage", required = false, defaultValue = "false") String removeImage,
                         RedirectAttributes redirectAttributes) {
        try {
            boolean doRemove = "true".equals(removeImage);
            productService.update(id, request, imageFile, doRemove);
            redirectAttributes.addFlashAttribute("successMsg", "Product updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/products";
    }

    @GetMapping("/view/{id}")
    public String view(@PathVariable Long id, Model model) {
        Product product = productService.findById(id);
        model.addAttribute("product", product);
        model.addAttribute("pageTitle", "Product Details");
        return "products/view";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            productService.delete(id);
            redirectAttributes.addFlashAttribute("successMsg", "Product deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/products";
    }

    // API endpoint for AJAX search
    @GetMapping("/api/search")
    @ResponseBody
    public ResponseEntity<ApiResponse<?>> searchApi(@RequestParam String keyword) {
        var products = productService.findAll(0, 20, keyword).getContent();
        return ResponseEntity.ok(ApiResponse.success("OK", products));
    }

    @GetMapping("/low-stock")
    public String lowStock(Model model) {
        model.addAttribute("products", productService.findLowStockProducts());
        model.addAttribute("pageTitle", "Low Stock Alerts");
        return "products/low-stock";
    }
}
