package com.stocksense.controller;

import com.stocksense.dto.response.ApiResponse;
import com.stocksense.repository.ForecastResultRepository;
import com.stocksense.service.AIIntegrationService;
import com.stocksense.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Controller
@RequestMapping("/forecasting")
@RequiredArgsConstructor
public class ForecastingController {

    private final AIIntegrationService aiService;
    private final ProductService productService;
    private final ForecastResultRepository forecastResultRepository;

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
            @RequestParam(defaultValue = "30") int days) {
        try {
            Map<String, Object> result = aiService.getForecast(productId, days);
            return ResponseEntity.ok(ApiResponse.success("Forecast generated", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/api/retrain")
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Object>>> retrain() {
        try {
            Map<String, Object> result = aiService.retrainModel();
            return ResponseEntity.ok(ApiResponse.success("Model retrained", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
