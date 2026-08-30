package com.stocksense.converter;

import com.stocksense.entity.Sale.PaymentMethod;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Payment Method Converter Tests")
class PaymentMethodConverterTest {

    private final PaymentMethodConverter converter = new PaymentMethodConverter();

    @Test
    @DisplayName("TC63 - payment converter: stores enum name")
    void convertToDatabaseColumn_enum_returnsName() {
        assertThat(converter.convertToDatabaseColumn(PaymentMethod.BANK_TRANSFER))
                .isEqualTo("BANK_TRANSFER");
    }

    @Test
    @DisplayName("TC64 - payment converter: reads case-insensitive values")
    void convertToEntityAttribute_lowercase_returnsEnum() {
        assertThat(converter.convertToEntityAttribute(" card ")).isEqualTo(PaymentMethod.CARD);
    }

    @Test
    @DisplayName("TC65 - payment converter: safely defaults invalid values to cash")
    void convertToEntityAttribute_invalid_returnsCash() {
        assertThat(converter.convertToEntityAttribute("MOBILE")).isEqualTo(PaymentMethod.CASH);
        assertThat(converter.convertToEntityAttribute("")).isEqualTo(PaymentMethod.CASH);
        assertThat(converter.convertToEntityAttribute(null)).isEqualTo(PaymentMethod.CASH);
    }
}
