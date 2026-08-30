package com.stocksense.service;

import com.stocksense.dto.request.ProductRequest;
import com.stocksense.entity.*;
import com.stocksense.repository.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Tests")
class ProductServiceTest {

    @Mock ProductRepository         productRepository;
    @Mock ProductCategoryRepository categoryRepository;
    @Mock SupplierRepository        supplierRepository;
    @Mock FileUploadService         fileUploadService;
    @Mock AuditLogService           auditLogService;
    @InjectMocks ProductService     productService;

    private ProductCategory category;
    private Product         product;
    private ProductRequest  request;

    @BeforeEach
    void setUp() {
        category = new ProductCategory();
        category.setId(1L);
        category.setName("Beverages");
        category.setIsActive(true);

        product = new Product();
        product.setId(1L);
        product.setName("Coca-Cola 330ml Can");
        product.setSku("BEV-001");
        product.setQuantity(100);
        product.setMinStockLevel(20);
        product.setBuyingPrice(BigDecimal.valueOf(55));
        product.setSellingPrice(BigDecimal.valueOf(80));
        product.setIsActive(true);
        product.setCategory(category);
        product.setUnit("can");

        request = new ProductRequest();
        request.setName("Coca-Cola 330ml Can");
        request.setSku("BEV-001");
        request.setUnit("can");
        request.setBuyingPrice(BigDecimal.valueOf(55));
        request.setSellingPrice(BigDecimal.valueOf(80));
        request.setQuantity(100);
        request.setMinStockLevel(20);
        request.setMaxStockLevel(500);
        request.setCategoryId(1L);
    }

    // ── TC01 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC01 - create: saves product when SKU is unique")
    void create_withUniqueSku_savesProduct() throws Exception {
        when(productRepository.existsBySku("BEV-001")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any())).thenReturn(product);
        doNothing().when(auditLogService).log(any(), any(), any(), any());

        Product result = productService.create(request, null);

        assertThat(result.getName()).isEqualTo("Coca-Cola 330ml Can");
        verify(productRepository).save(any(Product.class));
        verify(auditLogService).log(eq("PRODUCT_CREATED"), any(), any(), any());
    }

    // ── TC02 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC02 - create: throws when SKU already exists")
    void create_withDuplicateSku_throwsException() {
        when(productRepository.existsBySku("BEV-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SKU already exists");

        verify(productRepository, never()).save(any());
    }

    // ── TC03 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC03 - findById: returns correct product")
    void findById_existingId_returnsProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Product result = productService.findById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSku()).isEqualTo("BEV-001");
    }

    // ── TC04 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC04 - findById: throws when product does not exist")
    void findById_notFound_throwsException() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    // ── TC05 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC05 - update: removeImage=true sets imagePath to null")
    void update_removeImageTrue_clearsImagePath() throws Exception {
        product.setImagePath("uploads/products/old-image.png");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(auditLogService).log(any(), any(), any(), any());

        Product result = productService.update(1L, request, null, true);

        assertThat(result.getImagePath()).isNull();
    }

    // ── TC06 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC06 - delete: soft deletes product (sets isActive=false)")
    void delete_setsProductInactive() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(auditLogService).log(any(), any(), any(), any());

        productService.delete(1L);

        verify(productRepository).save(argThat(p -> Boolean.FALSE.equals(p.getIsActive())));
        verify(auditLogService).log(eq("PRODUCT_DELETED"), any(), any(), any());
    }

    // ── TC07 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC07 - countLowStock: returns count from repository")
    void countLowStock_returnsCorrectCount() {
        when(productRepository.countLowStockProducts()).thenReturn(3L);
        assertThat(productService.countLowStock()).isEqualTo(3L);
    }

    // ── TC08 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC08 - countActive: returns count from repository")
    void countActive_returnsCorrectCount() {
        when(productRepository.countActiveProducts()).thenReturn(15L);
        assertThat(productService.countActive()).isEqualTo(15L);
    }
}
