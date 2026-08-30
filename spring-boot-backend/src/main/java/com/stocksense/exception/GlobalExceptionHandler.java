package com.stocksense.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.stocksense.dto.response.ApiResponse;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Suppress favicon and static resource 404s - not an app error
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    // This class is a @RestControllerAdvice so it applies to EVERY controller,
    // including plain @Controller (Thymeleaf) page handlers - not just @RestController
    // API endpoints. If a page handler (e.g. GET /products/edit/{id}) throws, we must
    // NOT hand the browser a raw JSON body; only /api/** and XHR/JSON clients want that.
    // For normal page navigation, rethrow so Spring Boot's default error handling
    // (BasicErrorController -> a real HTML error page) takes over instead.
    private boolean wantsJson(HttpServletRequest request) {
        if (request == null) return true;
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) return true;
        String requestedWith = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equals(requestedWith)) return true;
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json") && !accept.contains("text/html");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex, HttpServletRequest request) {
        log.error("Business error: {}", ex.getMessage());
        if (!wantsJson(request)) {
            throw ex; // let it fall through to the default HTML error page for page requests
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        ApiResponse<Map<String, String>> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage("Validation failed");
        response.setData(errors);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied: you do not have permission"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("File too large. Maximum allowed size is 10MB."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex, HttpServletRequest request) throws Exception {
        log.error("Unhandled exception: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
        if (!wantsJson(request)) {
            throw ex; // let it fall through to the default HTML error page for page requests
        }
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("An internal error occurred. Please try again."));
    }
}
