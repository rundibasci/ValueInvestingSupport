package it.mazzoni.vis.portfolio.importing;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PortfolioCsvParserTest {
    private final PortfolioCsvParser parser = new PortfolioCsvParser();

    @Test
    void parsesSuppliedSchemaDecimalCommasSecurityAndCash() {
        String csv = "Prodotto,Codice,Quantità,Ultimo,Valore,,Valore in EUR\n"
                + "CASH & CASH FUND & FTX CASH (EUR),,,,EUR,\"8779,48\",\"8779,48\"\n"
                + "ACOMO NV,NL0000313286,570,\"23,45\",EUR,\"13366,50\",\"13366,50\"\n";
        List<ParsedPortfolioRow> rows = parser.parse(csv.getBytes(StandardCharsets.UTF_8), 10);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).status()).isEqualTo(ImportRowStatus.CASH);
        assertThat(rows.get(0).nativeValue()).isEqualByComparingTo("8779.48");
        assertThat(rows.get(1).isin()).isEqualTo("NL0000313286");
        assertThat(rows.get(1).quantity()).isEqualByComparingTo("570");
        assertThat(rows.get(1).sourceLastPrice()).isEqualByComparingTo("23.45");
        assertThat(rows.get(1).status()).isEqualTo(ImportRowStatus.NEEDS_MAPPING);
    }

    @Test
    void acceptsUtf8BomAndMarksBadIsinInvalidWithoutDroppingOtherRows() {
        String csv = "\uFEFFProdotto,Codice,Quantità,Ultimo,Valore,,Valore in EUR\n"
                + "Bad Corp,XX123,1,\"10,00\",EUR,\"10,00\",\"10,00\"\n"
                + "PFIZER INC,US7170811035,2,\"24,82\",USD,\"49,64\",\"43,30\"\n";
        List<ParsedPortfolioRow> rows = parser.parse(csv.getBytes(StandardCharsets.UTF_8), 10);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(rows.get(0).error()).contains("Invalid ISIN");
        assertThat(rows.get(1).status()).isEqualTo(ImportRowStatus.NEEDS_MAPPING);
    }

    @Test
    void rejectsDifferentSchemaAndRowLimit() {
        assertThatThrownBy(() -> parser.parse("a,b\n1,2".getBytes(StandardCharsets.UTF_8), 10))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Expected seven");
        String csv = "Prodotto,Codice,Quantità,Ultimo,Valore,,Valore in EUR\n"
                + "A,US0378331005,1,1,USD,1,1\nB,US7170811035,1,1,USD,1,1\n";
        assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8), 1))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("row limit");
    }
}
