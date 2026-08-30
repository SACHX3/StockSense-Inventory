package com.stocksense.service;

import com.stocksense.dto.request.SupplierRequest;
import com.stocksense.entity.Supplier;
import com.stocksense.repository.SupplierRepository;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupplierService Tests")
class SupplierServiceTest {

    @Mock SupplierRepository supplierRepository;
    @Mock AuditLogService    auditLogService;
    @InjectMocks SupplierService supplierService;

    private Supplier        supplier;
    private SupplierRequest request;

    @BeforeEach
    void setUp() {
        supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("Ceylon Beverages Ltd");
        supplier.setEmail("info@ceylonbev.lk");
        supplier.setPhone("+94 11 234 5678");
        supplier.setCity("Colombo");
        supplier.setCountry("Sri Lanka");
        supplier.setIsActive(true);

        request = new SupplierRequest();
        request.setName("Ceylon Beverages Ltd");
        request.setEmail("info@ceylonbev.lk");
        request.setPhone("+94 11 234 5678");
        request.setCity("Colombo");
        request.setCountry("Sri Lanka");
        request.setPaymentTerms("NET 30");
    }

    // ── TC15 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC15 - create: saves supplier and fires SUPPLIER_CREATED audit log")
    void create_savesSupplierAndLogs() {
        when(supplierRepository.save(any())).thenReturn(supplier);
        doNothing().when(auditLogService).log(any(), any(), any(), any());

        Supplier result = supplierService.create(request);

        assertThat(result.getName()).isEqualTo("Ceylon Beverages Ltd");
        verify(supplierRepository).save(any(Supplier.class));
        verify(auditLogService).log(eq("SUPPLIER_CREATED"), any(), any(), any());
    }

    // ── TC16 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC16 - update: updates supplier name and city correctly")
    void update_updatesSupplierFields() {
        request.setName("Ceylon Beverages Updated");
        request.setCity("Kandy");
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(auditLogService).log(any(), any(), any(), any());

        Supplier result = supplierService.update(1L, request);

        assertThat(result.getName()).isEqualTo("Ceylon Beverages Updated");
        assertThat(result.getCity()).isEqualTo("Kandy");
    }

    // ── TC17 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC17 - delete: sets isActive=false and fires SUPPLIER_DELETED audit log")
    void delete_setsInactiveAndLogs() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(auditLogService).log(any(), any(), any(), any());

        supplierService.delete(1L);

        verify(supplierRepository).save(argThat(s -> Boolean.FALSE.equals(s.getIsActive())));
        verify(auditLogService).log(eq("SUPPLIER_DELETED"), any(), any(), any());
    }

    // ── TC18 ─────────────────────────────────────────────────
    @Test
    @DisplayName("TC18 - findById: throws RuntimeException when supplier not found")
    void findById_notFound_throwsException() {
        when(supplierRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supplierService.findById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }
}
