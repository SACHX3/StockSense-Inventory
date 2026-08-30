package com.stocksense.service;

import com.stocksense.entity.Product;
import com.stocksense.repository.*;
import com.stocksense.dto.response.DashboardStats;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DashboardService Tests")
class DashboardServiceTest {

    @Mock ProductRepository productRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock UserRepository userRepository;
    @Mock SaleRepository saleRepository;
    @Mock SaleService saleService;
    @Mock SaleItemRepository saleItemRepository;
    @Mock ProductService productService;
    @Mock InventoryLogRepository inventoryLogRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock ForecastResultRepository forecastResultRepository;
    @Mock AIIntegrationService aiIntegrationService;
    @InjectMocks DashboardService dashboardService;

    @BeforeEach
    void setUpEmptyRepositories() {
        when(supplierRepository.findByIsActiveTrue()).thenReturn(Collections.emptyList());
        when(saleRepository.getDailyRevenue(any(), any())).thenReturn(Collections.emptyList());
        when(inventoryLogRepository.getDailyMovementTotals(any(), any())).thenReturn(Collections.emptyList());
        when(saleItemRepository.getDailyUnitsSold(any(), any())).thenReturn(Collections.emptyList());
        when(saleRepository.getMonthlyRevenue(any())).thenReturn(Collections.emptyList());
        when(saleItemRepository.findTopSellingProducts(any(), any())).thenReturn(Collections.emptyList());
        when(productRepository.findLowStockProducts()).thenReturn(Collections.emptyList());
        when(productRepository.findByIsActiveTrue()).thenReturn(Collections.emptyList());
        when(saleItemRepository.findCategoryTurnover(any(), any())).thenReturn(Collections.emptyList());
        when(saleRepository.findByDateRange(any(), any())).thenReturn(Collections.emptyList());
        when(invoiceRepository.findAll()).thenReturn(Collections.emptyList());
        when(productRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(forecastResultRepository.findByProductIdAndForecastDateAfterOrderByForecastDateAsc(anyLong(), any()))
                .thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("TC36 - dashboard: returns key totals from underlying services")
    void getDashboardStats_returnsSummaryTotals() {
        when(productService.countActive()).thenReturn(24L);
        when(productService.countLowStock()).thenReturn(4L);
        when(userRepository.count()).thenReturn(6L);
        when(saleService.getTodayRevenue()).thenReturn(new BigDecimal("1250.50"));
        when(saleService.getMonthlyRevenue()).thenReturn(new BigDecimal("9876.00"));
        when(saleService.getTodaySalesCount()).thenReturn(7L);

        DashboardStats stats = dashboardService.getDashboardStats(null, 7);

        assertThat(stats.getTotalProducts()).isEqualTo(24L);
        assertThat(stats.getLowStockProducts()).isEqualTo(4L);
        assertThat(stats.getTotalUsers()).isEqualTo(6L);
        assertThat(stats.getTodayRevenue()).isEqualByComparingTo("1250.50");
        assertThat(stats.getMonthlyRevenue()).isEqualByComparingTo("9876.00");
        assertThat(stats.getTodaySalesCount()).isEqualTo(7L);
    }

    @Test
    @DisplayName("TC37 - dashboard: creates requested number of daily chart points")
    void getDashboardStats_requestedRange_createsDailyPoints() {
        when(productService.countActive()).thenReturn(0L);
        when(productService.countLowStock()).thenReturn(0L);
        when(saleService.getTodayRevenue()).thenReturn(null);
        when(saleService.getMonthlyRevenue()).thenReturn(null);

        DashboardStats stats = dashboardService.getDashboardStats(null, 7);

        assertThat(stats.getDailySalesChart()).hasSize(7);
        assertThat(stats.getDailySalesChart())
                .allSatisfy(point -> assertThat(point.getRevenue()).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("TC38 - dashboard: groups stock health into healthy, low and critical")
    void getDashboardStats_stockHealth_countsCategories() {
        Product healthy = product(1L, "Healthy", 50, 10);
        Product low = product(2L, "Low", 10, 10);
        Product critical = product(3L, "Empty", 0, 10);
        when(productRepository.findByIsActiveTrue()).thenReturn(List.of(healthy, low, critical));
        when(productService.countActive()).thenReturn(3L);

        DashboardStats stats = dashboardService.getDashboardStats(null, 1);

        assertThat(stats.getStockHealth().getHealthy()).isEqualTo(1L);
        assertThat(stats.getStockHealth().getLow()).isEqualTo(1L);
        assertThat(stats.getStockHealth().getCritical()).isEqualTo(1L);
    }

    @Test
    @DisplayName("TC39 - dashboard: uses zero values when revenue services return null")
    void getDashboardStats_nullRevenue_usesZero() {
        when(saleService.getTodayRevenue()).thenReturn(null);
        when(saleService.getMonthlyRevenue()).thenReturn(null);

        DashboardStats stats = dashboardService.getDashboardStats();

        assertThat(stats.getTodayRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.getMonthlyRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private Product product(Long id, String name, int quantity, int minimum) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setQuantity(quantity);
        p.setMinStockLevel(minimum);
        p.setIsActive(true);
        return p;
    }
}
