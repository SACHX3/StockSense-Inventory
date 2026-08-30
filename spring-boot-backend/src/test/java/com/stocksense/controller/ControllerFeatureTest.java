package com.stocksense.controller;

import com.stocksense.config.AiServiceProcessManager;
import com.stocksense.dto.response.DashboardStats;
import com.stocksense.entity.*;
import com.stocksense.repository.*;
import com.stocksense.service.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Controller-level checks for the user-visible routes and JSON endpoints.
 * These tests call controllers directly, so they do not require a running
 * database or web server.
 */
@DisplayName("Controller Feature Tests")
class ControllerFeatureTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final InvoiceItemRepository invoiceItemRepository = mock(InvoiceItemRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProductService productService = mock(ProductService.class);
    private final SupplierService supplierService = mock(SupplierService.class);
    private final CategoryService categoryService = mock(CategoryService.class);
    private final UserService userService = mock(UserService.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final AIIntegrationService aiService = mock(AIIntegrationService.class);
    private final ForecastResultRepository forecastResultRepository = mock(ForecastResultRepository.class);
    private final AiServiceProcessManager aiServiceProcessManager = mock(AiServiceProcessManager.class);
    private final DashboardService dashboardService = mock(DashboardService.class);
    private final DashboardExportService dashboardExportService = mock(DashboardExportService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final FileUploadService fileUploadService = mock(FileUploadService.class);
    private final HttpSession session = mock(HttpSession.class);
    private final Authentication authentication = mock(Authentication.class);
    private final HttpServletRequest httpRequest = mock(HttpServletRequest.class);

    @Test
    @DisplayName("TC74 - notifications: returns low-stock alert details and critical state")
    void lowStockAlerts_outOfStockProduct_isCritical() {
        Product product = new Product();
        product.setId(7L);
        product.setName("Coffee");
        product.setSku("COF-01");
        product.setQuantity(0);
        product.setMinStockLevel(10);
        product.setUnit("pack");
        product.setIsActive(true);
        when(productRepository.findLowStockProducts()).thenReturn(List.of(product));

        var response = new NotificationController(productRepository).getLowStockAlerts();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("count")).isEqualTo(1);
        Map<?, ?> alert = (Map<?, ?>) ((List<?>) response.getBody().get("alerts")).get(0);
        assertThat(alert.get("critical")).isEqualTo(true);
        assertThat(alert.get("quantity")).isEqualTo(0);
    }

    @Test
    @DisplayName("TC75 - evidence: index shows only invoices already applied to stock")
    void evidenceIndex_filtersAppliedInvoices() {
        Invoice applied = new Invoice();
        applied.setId(1L);
        applied.setIsApplied(true);
        Invoice pending = new Invoice();
        pending.setId(2L);
        pending.setIsApplied(false);
        when(invoiceRepository.findAll()).thenReturn(List.of(applied, pending));
        when(supplierService.findAllActive()).thenReturn(Collections.emptyList());
        when(productService.findAllActive()).thenReturn(Collections.emptyList());
        Model model = new ExtendedModelMap();

        String view = new EvidenceController(supplierService, productService, invoiceRepository).index(model);

        assertThat(view).isEqualTo("evidence/index");
        assertThat(model.getAttribute("appliedInvoices")).isEqualTo(List.of(applied));
    }

    @Test
    @DisplayName("TC76 - authentication: authenticated users are redirected away from login")
    void loginPage_authenticatedUser_redirectsToDashboard() {
        when(authentication.isAuthenticated()).thenReturn(true);

        String view = new AuthController(userRepository, fileUploadService).loginPage(authentication);

        assertThat(view).isEqualTo("redirect:/dashboard");
    }

    @Test
    @DisplayName("TC77 - authentication: anonymous users receive the login view")
    void loginPage_anonymousUser_returnsLoginView() {
        assertThat(new AuthController(userRepository, fileUploadService).loginPage(null))
                .isEqualTo("auth/login");
    }

    @Test
    @DisplayName("TC78 - inventory API: returns the saved inventory log")
    void inventoryAdjustApi_success_returnsLog() {
        InventoryLog log = new InventoryLog();
        log.setId(9L);
        when(inventoryService.adjustStock(any())).thenReturn(log);

        var response = new InventoryController(inventoryService, productService)
                .adjustApi(new com.stocksense.dto.request.InventoryAdjustRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isSameAs(log);
    }

    @Test
    @DisplayName("TC79 - inventory API: converts adjustment errors to bad request")
    void inventoryAdjustApi_serviceError_returnsBadRequest() {
        when(inventoryService.adjustStock(any())).thenThrow(new RuntimeException("Insufficient stock"));

        var response = new InventoryController(inventoryService, productService)
                .adjustApi(new com.stocksense.dto.request.InventoryAdjustRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("Insufficient stock");
    }

    @Test
    @DisplayName("TC80 - forecasting API: remembers the predicted product in the session")
    void forecastingPredict_success_storesProductInSession() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("predictions", List.of());
        when(aiService.getForecast(4L, 14)).thenReturn(result);

        var response = new ForecastingController(aiService, productService,
                forecastResultRepository, aiServiceProcessManager).predict(4L, 14, session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(session).setAttribute("lastForecastProductId", 4L);
    }

    @Test
    @DisplayName("TC81 - forecasting API: reports unavailable AI service with 503")
    void forecastingHealth_unavailable_returnsServiceUnavailable() {
        when(aiService.checkServiceAvailability()).thenReturn(Map.of(
                "available", false, "message", "AI service unavailable"));

        var response = new ForecastingController(aiService, productService,
                forecastResultRepository, aiServiceProcessManager).checkAiService();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("TC82 - OCR API: saves edited quantity and product name as validated")
    void validateOcrItem_updatesAndValidatesItem() {
        InvoiceItem item = new InvoiceItem();
        item.setQuantity(2);
        item.setProductName("Old name");
        item.setIsValidated(false);
        when(invoiceItemRepository.findById(5L)).thenReturn(Optional.of(item));

        var updates = new HashMap<String, Object>();
        updates.put("quantity", 8);
        updates.put("productName", "Updated Coffee");
        updates.put("isValidated", true);
        var response = new OCRController(invoiceRepository, invoiceItemRepository,
                fileUploadService, aiService, supplierService).validateItem(5L, updates);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(item.getQuantity()).isEqualTo(8);
        assertThat(item.getProductName()).isEqualTo("Updated Coffee");
        assertThat(item.getIsValidated()).isTrue();
        verify(invoiceItemRepository).save(item);
    }

    @Test
    @DisplayName("TC83 - OCR API: reports unavailable OCR service with 503")
    void processOcr_serviceUnavailable_returns503() {
        when(aiService.processInvoice(3L)).thenReturn(Map.of(
                "status", "error", "message", "OCR service unavailable"));

        var response = new OCRController(invoiceRepository, invoiceItemRepository,
                fileUploadService, aiService, supplierService).processOcr(3L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getMessage()).contains("OCR service unavailable");
    }

    @Test
    @DisplayName("TC84 - OCR API: maps duplicate application errors to conflict")
    void applyOcr_duplicate_returnsConflict() {
        when(aiService.applyInvoiceToInventory(3L))
                .thenThrow(new RuntimeException("Invoice already applied"));

        var response = new OCRController(invoiceRepository, invoiceItemRepository,
                fileUploadService, aiService, supplierService).applyToInventory(3L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("TC85 - supplier API: limits search results to twenty rows")
    void supplierSearchApi_limitsResults() {
        List<Supplier> suppliers = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            Supplier supplier = new Supplier();
            supplier.setName("Supplier " + i);
            suppliers.add(supplier);
        }
        when(supplierService.search("supplier")).thenReturn(suppliers);

        var response = new SupplierController(supplierService, productRepository).searchApi("supplier");

        assertThat(response.getBody().getData()).hasSize(20);
    }

    @Test
    @DisplayName("TC86 - user management: create form loads available roles")
    void userCreateForm_loadsRoles() {
        Role role = new Role();
        role.setName("STAFF");
        when(userService.findAllRoles()).thenReturn(List.of(role));
        Model model = new ExtendedModelMap();

        String view = new UserController(userService).createForm(model);

        assertThat(view).isEqualTo("admin/user-form");
        assertThat(model.getAttribute("roles")).isEqualTo(List.of(role));
        assertThat(model.getAttribute("pageTitle")).isEqualTo("Add User");
    }

    @Test
    @DisplayName("TC87 - category management: list loads categories into the view")
    void categoryList_loadsCategories() {
        ProductCategory category = new ProductCategory();
        category.setName("Beverages");
        when(categoryService.findAll()).thenReturn(List.of(category));
        Model model = new ExtendedModelMap();

        String view = new CategoryController(categoryService, fileUploadService).list(model);

        assertThat(view).isEqualTo("products/categories");
        assertThat(model.getAttribute("categories")).isEqualTo(List.of(category));
    }

    @Test
    @DisplayName("TC88 - dashboard API: returns dashboard data from the service")
    void dashboardStats_success_returnsData() {
        DashboardStats stats = new DashboardStats();
        stats.setTotalProducts(12L);
        when(dashboardService.getDashboardStats(null)).thenReturn(stats);

        var response = new DashboardController(dashboardService, dashboardExportService).getStats(session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getTotalProducts()).isEqualTo(12L);
    }

    @Test
    @DisplayName("TC89 - audit logs: loads a paginated audit page")
    void auditList_loadsPage() {
        Page<AuditLog> page = new PageImpl<>(List.of(new AuditLog()));
        when(auditLogService.findAll(any())).thenReturn(page);
        Model model = new ExtendedModelMap();

        String view = new AuditController(auditLogService).list(model, 0);

        assertThat(view).isEqualTo("admin/audit");
        assertThat(model.getAttribute("logs")).isSameAs(page);
    }

    @Test
    @DisplayName("TC90 - access control: access denied page records the requested resource")
    void accessDenied_setsRequestedResourceAndStatus() {
        when(httpRequest.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn("/users");
        Model model = new ExtendedModelMap();

        String view = new AccessDeniedController().accessDenied(httpRequest, model);

        assertThat(view).isEqualTo("error/access-denied");
        assertThat(model.getAttribute("requestedResource")).isEqualTo("/users");
        verify(httpRequest).setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 403);
    }
}
