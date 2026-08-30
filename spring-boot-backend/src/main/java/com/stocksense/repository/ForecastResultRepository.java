package com.stocksense.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.stocksense.entity.ForecastResult;

import java.time.LocalDate;
import java.util.List;

public interface ForecastResultRepository extends JpaRepository<ForecastResult, Long> {
    List<ForecastResult> findByProductIdAndForecastDateAfterOrderByForecastDateAsc(Long productId, LocalDate date);
    List<ForecastResult> findTop30ByProductIdOrderByForecastDateDesc(Long productId);
    List<ForecastResult> findTop1ByOrderByCreatedAtDesc();
    boolean existsByProductId(Long productId);

    @Transactional
    void deleteByProductId(Long productId);
}
