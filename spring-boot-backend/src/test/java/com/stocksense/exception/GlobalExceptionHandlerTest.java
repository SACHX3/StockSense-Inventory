package com.stocksense.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Global Exception Handler Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("TC96 - exception handling: runtime API errors return bad request JSON")
    void handleRuntime_apiRequest_returnsBadRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/products");

        var response = handler.handleRuntime(new RuntimeException("Product not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Product not found");
    }

    @Test
    @DisplayName("TC97 - exception handling: access denied returns HTTP 403")
    void handleAccessDenied_returnsForbidden() {
        var response = handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).contains("Access denied");
    }

    @Test
    @DisplayName("TC98 - exception handling: oversized upload returns a clear limit message")
    void handleMaxUpload_returnsBadRequest() {
        var response = handler.handleMaxUpload(new MaxUploadSizeExceededException(10));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("10MB");
    }
}
