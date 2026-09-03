package com.stocksense.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "forecast_results")
@Data
@NoArgsConstructor
public class ForecastResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "forecast_date")
    private LocalDate forecastDate;

    @Column(name = "predicted_demand")
    private Integer predictedDemand;

    /** The same prediction before rounding. The Integer above is what the charts
     *  plot (both the dashboard sparkline and the Forecasting page use whole units),
     *  but averaging rounded values destroys slow movers: a product selling 0.4/day
     *  stores 0 every single day, so the mean comes out 0 and every downstream
     *  calculation - days until stockout, reorder quantity - silently gives up. */
    @Column(name = "predicted_demand_exact", precision = 12, scale = 3)
    private BigDecimal predictedDemandExact;

    @Column(name = "confidence_lower")
    private Integer confidenceLower;

    @Column(name = "confidence_upper")
    private Integer confidenceUpper;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(precision = 10, scale = 4)
    private BigDecimal mae;

    @Column(precision = 10, scale = 4)
    private BigDecimal rmse;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
