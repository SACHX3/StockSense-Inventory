package com.stocksense.service;

import com.stocksense.dto.request.InventoryAdjustRequest;
import com.stocksense.entity.*;
import com.stocksense.repository.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// lenient() prevents "unnecessary stubbing" errors —
// some stubs are shared in @BeforeEach but not every test uses all of them
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService Tests")
class InventoryServiceTest {

    @Mock ProductRepository      productRepository;
    @Mock InventoryLogRepository inventoryLogRepository;
    @Mock UserRepository         userRepository;
    @Mock AuditLogService        auditLogService;
    @InjectMocks InventoryService inventoryService;

    private Product               product;
    private InventoryAdjustRequest request;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(1L);
        product.setName("Coca-Cola 330ml Can");
        product.setSku("BEV-001");
        product.setQuantity(100);
        product.setIsActive(true);

        request = new InventoryAdjustRequest();
        request.setProductId(1L);
        request.setQuantity(10);
        request.setReferenceNo("TEST-REF-001");
        request.setNotes("Test adjustment");

        // Security context — used in every adjustStock call
        Authentication auth = mock(Authentication.class);
        SecurityContext ctx  = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        lenient().when(auth.getName()).thenReturn("admin");
        SecurityContextHolder.setContext(ctx);

        // userRepository is called inside adjustStock — make lenient so
        // tests that don't care about the user don't fail
        lenient().when(userRepository.findByUsername("admin"))
                 .thenReturn(Optional.empty());
    }

    // ── TC09 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC09 - adjustStock STOCK_IN: increases product quantity by request qty")
    void adjustStock_stockIn_increasesQuantity() {
        request.setMovementType("STOCK_IN");  // 100 + 10 = 110
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().doNothing().when(auditLogService).log(any(), any(), any(), any());

        inventoryService.adjustStock(request);

        verify(productRepository).save(argThat(p -> p.getQuantity() == 110));
    }

    // ── TC10 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC10 - adjustStock STOCK_OUT: decreases product quantity")
    void adjustStock_stockOut_decreasesQuantity() {
        request.setMovementType("STOCK_OUT");
        request.setQuantity(30);             // 100 - 30 = 70
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().doNothing().when(auditLogService).log(any(), any(), any(), any());

        inventoryService.adjustStock(request);

        verify(productRepository).save(argThat(p -> p.getQuantity() == 70));
    }

    // ── TC11 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC11 - adjustStock STOCK_OUT: throws when qty exceeds available stock")
    void adjustStock_stockOut_insufficientStock_throwsException() {
        request.setMovementType("STOCK_OUT");
        request.setQuantity(200);            // 200 > 100 available
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> inventoryService.adjustStock(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient");

        verify(productRepository, never()).save(any());
    }

    // ── TC12 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC12 - adjustStock ADJUSTMENT: sets quantity to the exact requested value")
    void adjustStock_adjustment_setsExactQuantity() {
        request.setMovementType("ADJUSTMENT");
        request.setQuantity(75);             // absolute set to 75
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().doNothing().when(auditLogService).log(any(), any(), any(), any());

        inventoryService.adjustStock(request);

        verify(productRepository).save(argThat(p -> p.getQuantity() == 75));
    }

    // ── TC13 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC13 - adjustStock: throws RuntimeException when product ID not found")
    void adjustStock_productNotFound_throwsException() {
        request.setMovementType("STOCK_IN");
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.adjustStock(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    // ── TC14 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC14 - adjustStock: inventory log records correct before/after quantities")
    void adjustStock_logsCorrectQuantities() {
        request.setMovementType("STOCK_IN");
        request.setQuantity(50);             // 100 + 50 = 150
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().doNothing().when(auditLogService).log(any(), any(), any(), any());

        inventoryService.adjustStock(request);

        verify(inventoryLogRepository).save(argThat(log ->
                log.getQuantityBefore() == 100 &&
                log.getQuantityAfter()  == 150 &&
                log.getMovementType()   == InventoryLog.MovementType.STOCK_IN
        ));
    }
}
