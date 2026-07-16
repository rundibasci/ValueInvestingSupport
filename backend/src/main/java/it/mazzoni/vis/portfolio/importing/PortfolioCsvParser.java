package it.mazzoni.vis.portfolio.importing;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Component
public class PortfolioCsvParser {
    private static final List<String> EXPECTED = List.of("prodotto", "codice", "quantita", "ultimo", "valore", "", "valore in eur");

    public List<ParsedPortfolioRow> parse(byte[] bytes, int maxRows) {
        try (var reader = new InputStreamReader(new ByteArrayInputStream(stripBom(bytes)), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).build().parse(reader)) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) throw badRequest("CSV file is empty");
            validateHeader(records.getFirst());
            if (records.size() - 1 > maxRows) throw badRequest("CSV row limit exceeded");
            List<ParsedPortfolioRow> result = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) result.add(parseRow(records.get(i), i + 1));
            return result;
        } catch (IOException | IllegalArgumentException ex) {
            if (ex instanceof ResponseStatusException response) throw response;
            throw badRequest("Malformed CSV file");
        }
    }

    private void validateHeader(CSVRecord record) {
        if (record.size() != EXPECTED.size()) throw badRequest("Expected seven CSV columns");
        for (int i = 0; i < EXPECTED.size(); i++) {
            if (!normalizeHeader(record.get(i)).equals(EXPECTED.get(i)))
                throw badRequest("Unsupported CSV header at column " + (i + 1));
        }
    }

    private ParsedPortfolioRow parseRow(CSVRecord r, int rowNumber) {
        if (r.size() != 7) return invalid(rowNumber, safe(r.size() > 0 ? r.get(0) : ""), "Expected seven columns");
        String product = safe(r.get(0));
        String code = blankToNull(r.get(1));
        String currency = upper(blankToNull(r.get(4)));
        boolean cash = product.toUpperCase(Locale.ROOT).startsWith("CASH & CASH FUND") && code == null;
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        BigDecimal quantity = number(r.get(2), "quantity", !cash, errors);
        BigDecimal last = number(r.get(3), "last price", false, errors);
        BigDecimal nativeValue = number(r.get(5), "native value", true, errors);
        BigDecimal baseValue = number(r.get(6), "base value", true, errors);
        if (product.isBlank()) errors.add("Product name is required");
        if (!validCurrency(currency)) errors.add("Valid ISO currency is required");
        String isin = code == null ? null : IsinValidator.normalize(code);
        if (!cash && code == null) errors.add("Security code is required");
        if (isin != null && !IsinValidator.isValid(isin)) errors.add("Invalid ISIN");
        if (!cash && quantity != null && last != null && nativeValue != null) {
            BigDecimal calculated = quantity.multiply(last);
            if (calculated.subtract(nativeValue).abs().compareTo(new BigDecimal("0.02")) > 0)
                warnings.add("Quantity multiplied by last price differs from native value");
        }
        if (!errors.isEmpty()) return new ParsedPortfolioRow(rowNumber, product, code, isin, quantity, last, currency,
                nativeValue, baseValue, cash ? "CASH" : "SECURITY", ImportRowStatus.INVALID,
                join(warnings), join(errors));
        return new ParsedPortfolioRow(rowNumber, product, code, isin, quantity, last, currency, nativeValue, baseValue,
                cash ? "CASH" : "SECURITY", cash ? ImportRowStatus.CASH : ImportRowStatus.NEEDS_MAPPING,
                join(warnings), null);
    }

    private BigDecimal number(String raw, String name, boolean required, List<String> errors) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) { if (required) errors.add(name + " is required"); return null; }
        try {
            BigDecimal result = new BigDecimal(value.replace(".", "").replace(',', '.'));
            if (result.precision() > 20 || result.scale() > 6) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException ex) {
            errors.add("Invalid " + name);
            return null;
        }
    }

    private boolean validCurrency(String value) {
        if (value == null || !value.matches("[A-Z]{3}")) return false;
        try { Currency.getInstance(value); return true; } catch (IllegalArgumentException ex) { return false; }
    }
    private ParsedPortfolioRow invalid(int row, String product, String error) {
        return new ParsedPortfolioRow(row, product, null, null, null, null, null, null, null,
                "UNKNOWN", ImportRowStatus.INVALID, null, error);
    }
    private byte[] stripBom(byte[] bytes) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF)
            return java.util.Arrays.copyOfRange(bytes, 3, bytes.length);
        return bytes;
    }
    private String normalizeHeader(String s) {
        return Normalizer.normalize(s == null ? "" : s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
    private String safe(String s) {
        String value = s == null ? "" : s.strip();
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
    private String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private String upper(String s) { return s == null ? null : s.toUpperCase(Locale.ROOT); }
    private String join(List<String> values) { return values.isEmpty() ? null : String.join("; ", values); }
    private ResponseStatusException badRequest(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
}
