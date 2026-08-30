package com.stocksense.dto.response;

import com.stocksense.entity.Supplier;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** Supplier-level stock coverage figures used by the supplier report. */
@Data
@AllArgsConstructor
public class SupplierCoverageRow {
    private Supplier supplier;
    private long linkedProductCount;
    private long totalUnits;
    private BigDecimal stockValue;
    private BigDecimal coveragePercent;
}
