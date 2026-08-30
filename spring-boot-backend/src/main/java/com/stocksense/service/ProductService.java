package com.stocksense.service;

import com.stocksense.dto.request.ProductRequest;
import com.stocksense.entity.*;
import com.stocksense.repository.*;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final FileUploadService fileUploadService;
    private final AuditLogService auditLogService;

    public Page<Product> findAll(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (keyword != null && !keyword.isEmpty()) {
            return productRepository.searchProducts(keyword, pageable);
        }
        return productRepository.findAllActiveProducts(pageable);
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    public List<Product> findLowStockProducts() {
        return productRepository.findLowStockProducts();
    }

    public List<Product> findAllActive() {
        return productRepository.findByIsActiveTrue();
    }

    @Transactional
    public Product create(ProductRequest request, MultipartFile imageFile) throws IOException {
        if (productRepository.existsBySku(request.getSku())) {
            throw new RuntimeException("SKU already exists: " + request.getSku());
        }

        Product product = mapToEntity(new Product(), request);

        if (imageFile != null && !imageFile.isEmpty()) {
            String imagePath = fileUploadService.uploadProductImage(imageFile);
            product.setImagePath(imagePath);
        }

        Product saved = productRepository.save(product);
        auditLogService.log("PRODUCT_CREATED", "Product", saved.getId(), "Created product: " + saved.getName());
        return saved;
    }

    @Transactional
    public Product update(Long id, ProductRequest request, MultipartFile imageFile) throws IOException {
        return update(id, request, imageFile, false);
    }

    public Product update(Long id, ProductRequest request, MultipartFile imageFile, boolean removeImage) throws IOException {
        Product product = findById(id);

        if (!product.getSku().equals(request.getSku()) && productRepository.existsBySku(request.getSku())) {
            throw new RuntimeException("SKU already exists: " + request.getSku());
        }

        String oldImagePath = product.getImagePath();
        mapToEntity(product, request);

        if (removeImage) {
            product.setImagePath(null);
        } else if (imageFile != null && !imageFile.isEmpty()) {
            String newPath = fileUploadService.uploadProductImage(imageFile);
            product.setImagePath(newPath);
            fileUploadService.deleteFile(oldImagePath);
        }

        Product saved = productRepository.save(product);
        auditLogService.log("PRODUCT_UPDATED", "Product", saved.getId(), "Updated product: " + saved.getName());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Product product = findById(id);
        product.setIsActive(false);
        productRepository.save(product);
        auditLogService.log("PRODUCT_DELETED", "Product", id, "Soft deleted product: " + product.getName());
    }

    private Product mapToEntity(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setBarcode(request.getBarcode());
        product.setDescription(request.getDescription());
        product.setUnit(request.getUnit());
        product.setBuyingPrice(request.getBuyingPrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setQuantity(request.getQuantity());
        product.setMinStockLevel(request.getMinStockLevel());
        product.setMaxStockLevel(request.getMaxStockLevel());

        ProductCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));
        product.setCategory(category);

        if (request.getSupplierId() != null) {
            supplierRepository.findById(request.getSupplierId()).ifPresent(product::setSupplier);
        }

        return product;
    }

    public long countLowStock() {
        return productRepository.countLowStockProducts();
    }

    public long countActive() {
        return productRepository.countActiveProducts();
    }
}
