package com.stocksense.service;

import com.stocksense.dto.request.SaleRequest;
import com.stocksense.entity.*;
import com.stocksense.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SaleService Tests")
class SaleServiceTest {

    @Mock SaleRepository         saleRepository;
    @Mock SaleItemRepository     saleItemRepository;
    @Mock ProductRepository      productRepository;
    @Mock UserRepository         userRepository;
    @Mock InventoryLogRepository inventoryLogRepository;
    @Mock AuditLogService        auditLogService;
    @InjectMocks SaleService     saleService;

    private Product     product;
    private SaleRequest request;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(1L);
        product.setName("Coca-Cola 330ml Can");
        product.setSku("BEV-001");
        product.setQuantity(100);
        product.setSellingPrice(BigDecimal.valueOf(80));

        SaleRequest.SaleItemRequest itemReq = new SaleRequest.SaleItemRequest();
        itemReq.setProductId(1L);
        itemReq.setQuantity(5);
        itemReq.setUnitPrice(BigDecimal.valueOf(80));
        itemReq.setDiscountPercent(BigDecimal.ZERO);

        request = new SaleRequest();
        request.setCustomerName("Walk-in Customer");
        request.setPaymentMethod("CASH");
        request.setDiscountAmount(BigDecimal.ZERO);
        request.setItems(List.of(itemReq));

        // Security context — lenient so tests that throw early don't fail
        Authentication auth = mock(Authentication.class);
        SecurityContext ctx  = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        lenient().when(auth.getName()).thenReturn("admin");
        SecurityContextHolder.setContext(ctx);
        lenient().when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
    }

    // ── TC19 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC19 - createSale: deducts stock correctly and creates STOCK_OUT log")
    void createSale_deductsStockAndCreatesLog() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(saleRepository.count()).thenReturn(0L);

        Sale savedSale = new Sale();
        savedSale.setId(1L);
        savedSale.setInvoiceNumber("INV-202606-0001");
        savedSale.setTotalAmount(BigDecimal.valueOf(400));
        savedSale.setItems(new ArrayList<>());
        when(saleRepository.save(any())).thenReturn(savedSale);
        lenient().doNothing().when(auditLogService).log(any(), any(), any(), any());

        Sale result = saleService.createSale(request);

        // Stock must be reduced: 100 - 5 = 95
        verify(productRepository).save(argThat(p -> p.getQuantity() == 95));
        // Must log a STOCK_OUT entry for qty=5
        verify(inventoryLogRepository).save(argThat(log ->
                log.getMovementType() == InventoryLog.MovementType.STOCK_OUT &&
                log.getQuantity() == 5
        ));
        assertThat(result.getInvoiceNumber()).startsWith("INV-");
    }

    // ── TC20 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC20 - createSale: throws exception when stock is insufficient")
    void createSale_insufficientStock_throwsAndDoesNotSave() {
        product.setQuantity(3);  // only 3 in stock, request wants 5
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(saleRepository.count()).thenReturn(0L);

        assertThatThrownBy(() -> saleService.createSale(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient stock");

        // Stock must NOT be changed when there's not enough
        verify(productRepository, never()).save(any());
        verify(inventoryLogRepository, never()).save(any());
    }
}
