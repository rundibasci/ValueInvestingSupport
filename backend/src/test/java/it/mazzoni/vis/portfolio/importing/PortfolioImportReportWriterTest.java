package it.mazzoni.vis.portfolio.importing;

import it.mazzoni.vis.domain.entity.PortfolioImport;
import it.mazzoni.vis.domain.entity.PortfolioImportRow;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class PortfolioImportReportWriterTest {
    @Test void writesUtf8QuotedFormulaSafeRowsAndTotals() {
        PortfolioImport portfolioImport = new PortfolioImport();
        portfolioImport.setBaseCurrency("EUR");
        portfolioImport.setStatus("COMMITTED");
        PortfolioImportRow row = new PortfolioImportRow();
        row.setRowNumber(2); row.setProductName("=HYPERLINK(\"bad\")"); row.setIsin("US0378331005");
        row.setQuantity(new BigDecimal("2")); row.setSourceLastPrice(new BigDecimal("10.25"));
        row.setNativeCurrency("USD"); row.setNativeValue(new BigDecimal("20.50"));
        row.setBaseValue(new BigDecimal("18.75")); row.setClassification("SECURITY");
        row.setStatus("READY"); row.setCommittedOutcome("COMMITTED");
        portfolioImport.addRow(row);
        String report = new String(PortfolioImportReportWriter.write(portfolioImport), StandardCharsets.UTF_8);
        assertThat(report).startsWith("\uFEFFRow,");
        assertThat(report).contains("'=HYPERLINK");
        assertThat(report).contains("TOTAL BASE").contains("18.75");
        assertThat(report).contains("TOTAL NATIVE").contains("USD").contains("20.50");
    }
}
