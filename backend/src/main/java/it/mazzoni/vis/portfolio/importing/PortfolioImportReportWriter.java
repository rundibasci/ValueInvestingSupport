package it.mazzoni.vis.portfolio.importing;

import it.mazzoni.vis.domain.entity.PortfolioImport;
import it.mazzoni.vis.domain.entity.PortfolioImportRow;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class PortfolioImportReportWriter {
    private PortfolioImportReportWriter() { }

    static byte[] write(PortfolioImport portfolioImport) {
        try {
            StringWriter output = new StringWriter();
            output.write('\uFEFF');
            try (CSVPrinter csv = new CSVPrinter(output, CSVFormat.DEFAULT)) {
                csv.printRecord("Row", "Product", "ISIN", "Quantity", "Source last price", "Currency",
                        "Native value", "Base currency", "Base value", "Resolved symbol", "Classification",
                        "Status", "Warning", "Error", "Committed outcome");
                BigDecimal baseTotal = BigDecimal.ZERO;
                Map<String, BigDecimal> nativeTotals = new LinkedHashMap<>();
                for (PortfolioImportRow row : portfolioImport.getRows()) {
                    csv.printRecord(row.getRowNumber(), safe(row.getProductName()), safe(row.getIsin()),
                            row.getQuantity(), row.getSourceLastPrice(), safe(row.getNativeCurrency()),
                            row.getNativeValue(), safe(portfolioImport.getBaseCurrency()), row.getBaseValue(),
                            safe(row.getResolvedSecurity() == null ? null : row.getResolvedSecurity().getSymbol()),
                            safe(row.getClassification()), safe(row.getStatus()), safe(row.getWarningText()),
                            safe(row.getErrorText()), safe(row.getCommittedOutcome()));
                    if (!"SKIPPED".equals(row.getCommittedOutcome())) {
                        if (row.getBaseValue() != null) baseTotal = baseTotal.add(row.getBaseValue());
                        if (row.getNativeCurrency() != null && row.getNativeValue() != null)
                            nativeTotals.merge(row.getNativeCurrency(), row.getNativeValue(), BigDecimal::add);
                    }
                }
                csv.printRecord();
                csv.printRecord("TOTAL BASE", "", "", "", "", "", "", portfolioImport.getBaseCurrency(),
                        baseTotal, "", "", portfolioImport.getStatus(), "", "", "");
                for (var entry : nativeTotals.entrySet())
                    csv.printRecord("TOTAL NATIVE", "", "", "", "", entry.getKey(), entry.getValue());
            }
            return output.toString().getBytes(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create reconciliation report", ex);
        }
    }

    private static String safe(String value) {
        if (value == null) return "";
        String sanitized = value.replace("\u0000", "");
        return !sanitized.isEmpty() && "=+-@".indexOf(sanitized.charAt(0)) >= 0 ? "'" + sanitized : sanitized;
    }
}
