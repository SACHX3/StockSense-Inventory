package com.stocksense.converter;

import com.stocksense.entity.Sale.PaymentMethod;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * Lenient converter for Sale.payment_method.
 *
 * The default @Enumerated(EnumType.STRING) mapping throws an unrecoverable
 * IllegalArgumentException (surfaced as a 500 on /sales) the moment ANY row in
 * the table has a payment_method value that isn't an exact match for one of
 * the enum constants - including stray legacy values, typos, or values from an
 * earlier version of imported/generated seed data (e.g. "MOBILE", blank
 * strings, different casing). One bad row breaks the entire page, since
 * Hibernate fails while reading the whole result list, not just that row.
 *
 * This converter reads any unrecognized value as CASH instead of throwing, so
 * the app stays usable even with imperfect historical data, and logs a
 * warning so it's visible in the console which row/value needs cleanup.
 */
@Slf4j
@Converter(autoApply = true)
public class PaymentMethodConverter implements AttributeConverter<PaymentMethod, String> {

    @Override
    public String convertToDatabaseColumn(PaymentMethod attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public PaymentMethod convertToEntityAttribute(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) {
            return PaymentMethod.CASH;
        }
        try {
            return PaymentMethod.valueOf(dbValue.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unrecognized payment_method value '{}' in sales table - defaulting to CASH. " +
                      "Run: UPDATE sales SET payment_method = 'CASH' WHERE payment_method = '{}'; to fix permanently.",
                      dbValue, dbValue);
            return PaymentMethod.CASH;
        }
    }
}
