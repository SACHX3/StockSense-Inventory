package com.stocksense.controller;

import com.stocksense.dto.request.SaleRequest;
import com.stocksense.dto.response.ApiResponse;
import com.stocksense.entity.Product;
import com.stocksense.entity.Sale;
import com.stocksense.service.ProductService;
import com.stocksense.service.SaleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SalesController / POS Tests")
class SalesControllerTest {

    @Mock SaleService saleService;
    @Mock ProductService productService;
    @InjectMocks SalesController salesController;

    @Test
    @DisplayName("TC40 - POS page: loads active products and the sales/pos view")
    void createForm_loadsProductsForPosPage() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Coffee");
        when(productService.findAllActive()).thenReturn(List.of(product));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = salesController.createForm(model);

        assertThat(view).isEqualTo("sales/pos");
        assertThat(model.getAttribute("products")).isEqualTo(List.of(product));
        assertThat(model.getAttribute("pageTitle")).isEqualTo("New Sale (POS)");
    }

    @Test
    @DisplayName("TC41 - POS create endpoint: returns a flat success payload for the receipt link")
    void createSale_success_returnsFlatResponse() {
        Sale sale = new Sale();
        sale.setId(44L);
        sale.setInvoiceNumber("INV-202608-0001-1234");
        sale.setCustomerName("Walk-in Customer");
        sale.setCustomerPhone("0712345678");
        sale.setSubtotal(new BigDecimal("400.00"));
        sale.setDiscountAmount(BigDecimal.ZERO);
        sale.setTotalAmount(new BigDecimal("400.00"));
        sale.setPaymentMethod(Sale.PaymentMethod.CASH);
        sale.setPaymentStatus(Sale.PaymentStatus.PAID);
        sale.setItems(new ArrayList<>());
        when(saleService.createSale(any(SaleRequest.class))).thenReturn(sale);

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                salesController.createSale(new SaleRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData())
                .containsEntry("id", 44L)
                .containsEntry("invoiceNumber", "INV-202608-0001-1234")
                .containsEntry("total", new BigDecimal("400.00"))
                .containsEntry("itemCount", 0);
    }

    @Test
    @DisplayName("TC42 - POS create endpoint: converts service errors to a bad-request response")
    void createSale_serviceError_returnsBadRequest() {
        when(saleService.createSale(any(SaleRequest.class)))
                .thenThrow(new RuntimeException("Insufficient stock for Coffee"));

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                salesController.createSale(new SaleRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("Insufficient stock");
    }
}
