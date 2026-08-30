package com.stocksense.controller;

import com.stocksense.config.AiServiceProcessManager;
import com.stocksense.dto.response.ApiResponse;
import com.stocksense.repository.ForecastResultRepository;
import com.stocksense.service.AIIntegrationService;
import com.stocksense.service.ProductService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/forecasting")
@RequiredArgsConstructor
@Slf4j
public class ForecastingController {

    private final AIIntegrationService aiService;
    private final ProductService productService;
    private final ForecastResultRepository forecastResultRepository;
    private final AiServiceProcessManager aiServiceProcessManager;

    @GetMapping
    public String forecastPage(Model model) {
        model.addAttribute("products", productService.findAllActive());
        model.addAttribute("pageTitle", "AI Demand Forecasting");
        return "forecasting/index";
    }

    @GetMapping("/product/{productId}")
    public String productForecast(@PathVariable Long productId, Model model,
                                  @RequestParam(defaultValue = "30") int days) {
        model.addAttribute("product", productService.findById(productId));
        model.addAttribute("forecastDays", days);
        model.addAttribute("pageTitle", "Product Forecast");
        var results = forecastResultRepository
                .findByProductIdAndForecastDateAfterOrderByForecastDateAsc(productId, LocalDate.now().minusDays(1));
        model.addAttribute("forecastResults", results);
        return "forecasting/product";
    }

    @PostMapping("/api/predict/{productId}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Object>>> predict(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "30") int days,
            HttpSession session) {
        try {
            Map<String, Object> result = aiService.getForecast(productId, days);
            String message = "fallback".equalsIgnoreCase(String.valueOf(result.get("status")))
                    ? String.valueOf(result.getOrDefault("message", "AI service unavailable; fallback forecast displayed"))
                    : "Forecast generated";
            // Remember this product for this session so the dashboard's AI Forecast
            // widget switches to show it, overriding the default low-stock pick.
            session.setAttribute("lastForecastProductId", productId);
            return ResponseEntity.ok(ApiResponse.success(message, result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/health")
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkAiService() {
        Map<String, Object> result = aiService.checkServiceAvailability();
        boolean available = Boolean.TRUE.equals(result.get("available"));
        String message = String.valueOf(result.get("message"));
        if (!available) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(message));
        }
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    // Manual start/stop for the topbar AI-service controls. Starting a Python/Uvicorn
    // process can take a while (venv setup, deps, model load), so this kicks off the
    // real AiServiceProcessManager.startAiServiceIfNeeded() on a background thread and
    // returns immediately - the client is expected to poll /api/health afterward.
    @PostMapping("/api/service/start")
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Object>>> startAiService() {
        if (aiServiceProcessManager.checkHealthy()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "already-running");
            return ResponseEntity.ok(ApiResponse.success("AI service is already running.", data));
        }
        Thread t = new Thread(() -> {
            boolean ok = aiServiceProcessManager.startAiServiceIfNeeded();
            log.info("Manual AI service start requested from UI -> {}", ok ? "started" : "failed: " + aiServiceProcessManager.getLastError());
        }, "ai-service-manual-start");
        t.setDaemon(true);
        t.start();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "starting");
        return ResponseEntity.accepted().body(ApiResponse.success("Starting AI service… this can take up to a couple of minutes on first run.", data));
    }

    @PostMapping("/api/service/stop")
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Object>>> stopAiService() {
        boolean stopped = aiServiceProcessManager.stopAiServiceManually();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", stopped ? "stopped" : "not-running");
        if (!stopped) {
            String err = aiServiceProcessManager.getLastError();
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(err != null ? err : "AI service isn't running."));
        }
        return ResponseEntity.ok(ApiResponse.success("AI service stopped.", data));
    }

    // Lightweight status check for the topbar button's initial render / periodic poll:
    // reports whether the service is healthy AND whether it's a process this app
    // itself launched (so "Stop" can be disabled for a service started outside the app).
    @GetMapping("/api/service/status")
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Object>>> aiServiceStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("healthy", aiServiceProcessManager.checkHealthy());
        data.put("managedByApp", aiServiceProcessManager.isManagedProcessAlive());
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    @PostMapping("/api/retrain")
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Object>>> retrain() {
        try {
            Map<String, Object> result = aiService.retrainModel();
            if ("error".equalsIgnoreCase(String.valueOf(result.get("status")))) {
                String message = String.valueOf(result.getOrDefault("message", "AI service unavailable"));
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error(message));
            }
            return ResponseEntity.ok(ApiResponse.success("Model retrained", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
