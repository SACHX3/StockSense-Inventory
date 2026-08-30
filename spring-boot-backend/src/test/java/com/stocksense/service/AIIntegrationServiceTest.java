package com.stocksense.service;

import com.stocksense.entity.*;
import com.stocksense.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AIIntegrationService Tests")
class AIIntegrationServiceTest {

    @Mock SaleItemRepository saleItemRepository;
    @Mock ProductRepository productRepository;
    @Mock ForecastResultRepository forecastResultRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock InvoiceItemRepository invoiceItemRepository;
    @Mock InventoryService inventoryService;
    @Mock RestTemplate restTemplate;
    @InjectMocks AIIntegrationService aiIntegrationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiIntegrationService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(aiIntegrationService, "aiServiceUrl", "http://localhost:8000");
    }

    @Test
    @DisplayName("TC29 - checkServiceAvailability: returns available when FastAPI responds successfully")
    void checkServiceAvailability_healthyService_returnsAvailable() {
        when(restTemplate.getForEntity("http://localhost:8000/health", Map.class))
                .thenReturn(ResponseEntity.ok(Map.of("status", "healthy")));

        Map<String, Object> result = aiIntegrationService.checkServiceAvailability();

        assertThat(result).containsEntry("available", true);
    }

    @Test
    @DisplayName("TC30 - checkServiceAvailability: degrades safely when FastAPI is offline")
    void checkServiceAvailability_offlineService_returnsUnavailable() {
        when(restTemplate.getForEntity("http://localhost:8000/health", Map.class))
                .thenThrow(new RestClientException("connection refused"));

        Map<String, Object> result = aiIntegrationService.checkServiceAvailability();

        assertThat(result).containsEntry("available", false);
        assertThat(result.get("message").toString()).contains("Core inventory remains available");
    }

    @Test
    @DisplayName("TC31 - getForecast: returns fallback forecast when FastAPI is offline")
    void getForecast_offlineService_returnsFallbackForecast() {
        when(saleItemRepository.findDailySalesForProduct(eq(9L), any())).thenReturn(Collections.emptyList());
        when(restTemplate.postForEntity(eq("http://localhost:8000/api/forecast/predict"),
                any(), eq(Map.class))).thenThrow(new RestClientException("connection refused"));

        Map<String, Object> result = aiIntegrationService.getForecast(9L, 7);

        assertThat(result).containsEntry("status", "fallback");
        assertThat(result).containsEntry("product_id", 9L);
        assertThat((List<?>) result.get("predictions")).hasSize(7);
        verify(forecastResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC32 - processInvoice: marks invoice failed when OCR service is unavailable")
    void processInvoice_offlineService_marksInvoiceFailed() {
        Invoice invoice = new Invoice();
        invoice.setId(3L);
        invoice.setFilePath("uploads/invoices/test.pdf");
        invoice.setFileType(Invoice.FileType.PDF);
        when(invoiceRepository.findById(3L)).thenReturn(Optional.of(invoice));
        when(restTemplate.postForEntity(eq("http://localhost:8000/api/ocr/process"),
                any(), eq(Map.class))).thenThrow(new RestClientException("connection refused"));

        Map<String, Object> result = aiIntegrationService.processInvoice(3L);

        assertThat(result).containsEntry("status", "error");
        assertThat(invoice.getOcrStatus()).isEqualTo(Invoice.OcrStatus.FAILED);
        verify(invoiceRepository, atLeast(2)).save(invoice);
    }

    @Test
    @DisplayName("TC33 - processInvoice: throws when invoice does not exist")
    void processInvoice_missingInvoice_throwsException() {
        when(invoiceRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiIntegrationService.processInvoice(404L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invoice not found");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("TC34 - applyInvoiceToInventory: blocks a second application")
    void applyInvoice_alreadyApplied_blocksDuplicate() {
        Invoice invoice = new Invoice();
        invoice.setId(8L);
        invoice.setInvoiceNumber("SUP-100");
        invoice.setIsApplied(true);
        when(invoiceRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> aiIntegrationService.applyInvoiceToInventory(8L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Duplicate OCR application blocked");
        verifyNoInteractions(inventoryService);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC35 - applyInvoiceToInventory: applies linked item and marks invoice applied")
    void applyInvoice_validItem_updatesInventory() {
        Invoice invoice = new Invoice();
        invoice.setId(10L);
        invoice.setInvoiceNumber("SUP-101");
        invoice.setIsApplied(false);

        Product product = new Product();
        product.setId(20L);
        product.setName("Coffee");

        InvoiceItem item = new InvoiceItem();
        item.setInvoice(invoice);
        item.setProduct(product);
        item.setProductName("Coffee");
        item.setQuantity(12);
        invoice.setItems(List.of(item));

        when(invoiceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findAppliedByInvoiceNumberForUpdate("SUP-101", 10L))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = aiIntegrationService.applyInvoiceToInventory(10L);

        assertThat(result).containsEntry("status", "success");
        assertThat(result).containsEntry("applied_items", 1);
        assertThat(result).containsEntry("skipped_items", 0);
        assertThat(invoice.getIsApplied()).isTrue();
        verify(inventoryService).adjustStock(any());
        verify(invoiceItemRepository).save(item);
        verify(invoiceRepository).save(invoice);
    }
}
