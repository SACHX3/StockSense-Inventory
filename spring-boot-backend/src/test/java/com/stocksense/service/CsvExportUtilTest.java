package com.stocksense.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CSV Export Utility Tests")
class CsvExportUtilTest {

    @Test
    @DisplayName("TC50 - CSV: writes headers and rows in UTF-8")
    void toCsv_writesHeadersAndRows() throws Exception {
        byte[] csv = CsvExportUtil.toCsv(
                List.of("SKU", "Product"),
                List.of(List.of("BEV-001", "Coca-Cola")));

        assertThat(new String(csv, StandardCharsets.UTF_8))
                .isEqualTo("SKU,Product\r\nBEV-001,Coca-Cola\r\n");
    }

    @Test
    @DisplayName("TC51 - CSV: quotes commas, quotes and line breaks according to RFC 4180")
    void toCsv_escapesSpecialCharacters() throws Exception {
        byte[] csv = CsvExportUtil.toCsv(
                List.of("Description"),
                List.of(List.of("Tea, \"premium\"\npack")));

        assertThat(new String(csv, StandardCharsets.UTF_8))
                .isEqualTo("Description\r\n\"Tea, \"\"premium\"\"\npack\"\r\n");
    }

    @Test
    @DisplayName("TC52 - CSV: converts null values to empty fields")
    void toCsv_nullValue_isEmptyField() throws Exception {
        // List.of rejects null elements before the CSV utility can receive them.
        byte[] csv = CsvExportUtil.toCsv(
                List.of("A", "B"),
                List.of(Arrays.asList(null, "value")));

        assertThat(new String(csv, StandardCharsets.UTF_8)).contains("\r\n,value\r\n");
    }
}
