package com.stocksense.service;

import com.stocksense.entity.AuditLog;
import com.stocksense.repository.AuditLogRepository;
import com.stocksense.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Audit Log Service Tests")
class AuditLogServiceTest {

    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;
    @Mock HttpServletRequest request;
    @InjectMocks AuditLogService auditLogService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("TC91 - audit: records the authenticated username and event details")
    void log_authenticatedUser_savesAuditRecord() {
        Authentication authentication = mock(Authentication.class);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin");
        SecurityContextHolder.setContext(context);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        auditLogService.log("PRODUCT_CREATED", "Product", 7L, "Created product");

        verify(auditLogRepository).save(argThat(log ->
                log.getUsername().equals("admin")
                        && log.getAction().equals("PRODUCT_CREATED")
                        && log.getEntityType().equals("Product")
                        && log.getEntityId().equals(7L)
                        && log.getNewValues().equals("Created product")));
    }

    @Test
    @DisplayName("TC92 - audit: records forwarded IP and user-agent headers")
    void logWithRequest_recordsClientMetadata() {
        Authentication authentication = mock(Authentication.class);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("staff1");
        SecurityContextHolder.setContext(context);
        when(userRepository.findByUsername("staff1")).thenReturn(Optional.empty());
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("StockSense-Test");

        auditLogService.log("SALE_CREATED", "Sale", 10L, "old", "new", request);

        verify(auditLogRepository).save(argThat(log ->
                "203.0.113.10".equals(log.getIpAddress())
                        && "StockSense-Test".equals(log.getUserAgent())
                        && "old".equals(log.getOldValues())
                        && "new".equals(log.getNewValues())));
    }

    @Test
    @DisplayName("TC93 - audit: repository failures do not interrupt the business operation")
    void log_repositoryFailure_isSwallowed() {
        when(userRepository.findByUsername("system")).thenThrow(new RuntimeException("database offline"));

        assertThatCode(() -> auditLogService.log("TEST", "System", 1L, "details"))
                .doesNotThrowAnyException();
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC94 - audit: returns newest logs through the paginated repository query")
    void findAll_delegatesPageRequest() {
        Page<AuditLog> page = new PageImpl<>(List.of(new AuditLog()));
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(page);

        assertThat(auditLogService.findAll(PageRequest.of(0, 50))).isSameAs(page);
    }
}
