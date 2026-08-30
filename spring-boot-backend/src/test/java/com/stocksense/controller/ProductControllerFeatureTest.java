package com.stocksense.controller;

import com.stocksense.entity.Product;
import com.stocksense.service.CategoryService;
import com.stocksense.service.ProductService;
import com.stocksense.service.SupplierService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.ui.ExtendedModelMap;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Product Controller Feature Tests")
class ProductControllerFeatureTest {

    private final ProductService productService = mock(ProductService.class);
    private final CategoryService categoryService = mock(CategoryService.class);
    private final SupplierService supplierService = mock(SupplierService.class);

    @Test
    @DisplayName("TC99 - products: list includes active products and low-stock count")
    void list_loadsProductPage() {
        Product product = new Product();
        product.setName("Coffee");
        when(productService.findAll(0, 15, "coffee"))
                .thenReturn(new PageImpl<>(List.of(product)));
        when(productService.countLowStock()).thenReturn(1L);
        var model = new ExtendedModelMap();

        String view = new ProductController(productService, categoryService, supplierService)
                .list(model, 0, 15, "coffee");

        assertThat(view).isEqualTo("products/list");
        assertThat(model.getAttribute("products")).isInstanceOf(PageImpl.class);
        assertThat(model.getAttribute("lowStockCount")).isEqualTo(1L);
    }

    @Test
    @DisplayName("TC100 - products: create form loads categories and suppliers")
    void createForm_loadsReferenceData() {
        when(categoryService.findAllActive()).thenReturn(List.of());
        when(supplierService.findAllActive()).thenReturn(List.of());
        var model = new ExtendedModelMap();

        String view = new ProductController(productService, categoryService, supplierService).createForm(model);

        assertThat(view).isEqualTo("products/form");
        assertThat(model.getAttribute("product")).isNotNull();
        assertThat(model.getAttribute("categories")).isEqualTo(List.of());
        assertThat(model.getAttribute("suppliers")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("TC101 - products API: returns search results in the standard response wrapper")
    void searchApi_returnsProducts() {
        Product product = new Product();
        product.setName("Coffee");
        when(productService.findAll(0, 20, "coffee"))
                .thenReturn(new PageImpl<>(Collections.singletonList(product)));

        var response = new ProductController(productService, categoryService, supplierService)
                .searchApi("coffee");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo(List.of(product));
    }

    @Test
    @DisplayName("TC102 - products: low-stock page loads the low-stock product list")
    void lowStock_loadsProducts() {
        Product product = new Product();
        product.setQuantity(0);
        when(productService.findLowStockProducts()).thenReturn(List.of(product));
        var model = new ExtendedModelMap();

        String view = new ProductController(productService, categoryService, supplierService).lowStock(model);

        assertThat(view).isEqualTo("products/low-stock");
        assertThat(model.getAttribute("products")).isEqualTo(List.of(product));
    }
}
