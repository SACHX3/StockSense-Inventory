package com.stocksense.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Minimal, dependency-free CSV writer shared by every report's CSV export
 * (Sales / Inventory / Suppliers / Custom Report Builder). Handles quoting
 * of values containing commas, quotes, or newlines per RFC 4180.
 */
public final class CsvExportUtil {

    private CsvExportUtil() { }

    public static byte[] toCsv(List<String> headers, List<List<String>> rows) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            // CSV records use CRLF consistently, independent of the host OS.
            writer.print(toLine(headers));
            writer.print("\r\n");
            for (List<String> row : rows) {
                writer.print(toLine(row));
                writer.print("\r\n");
            }
        }
        return out.toByteArray();
    }

    private static String toLine(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(values.get(i)));
        }
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }
}
