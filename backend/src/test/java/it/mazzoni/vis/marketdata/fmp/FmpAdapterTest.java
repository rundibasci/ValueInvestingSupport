package it.mazzoni.vis.marketdata.fmp;

import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.marketdata.fmp.dto.FmpBalanceSheetEntry;
import it.mazzoni.vis.marketdata.fmp.dto.FmpCashFlowEntry;
import it.mazzoni.vis.marketdata.fmp.dto.FmpIncomeStatementEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RM1 (specs/sector-aware-valuation-metrics.md): verifies the new depreciationAndAmortization/
 * ebitda history mapping added to FFO/Debt-EBITDA data ingestion.
 */
class FmpAdapterTest {

    private final FmpAdapter adapter = new FmpAdapter();

    @Test
    void toFundamentalSnapshot_mapsDepreciationAndEbitdaHistoriesFromIncomeStatement() {
        FmpIncomeStatementEntry latest = new FmpIncomeStatementEntry(
                "O", "2025-12-31",
                new BigDecimal("1000000000"), new BigDecimal("300000000"), new BigDecimal("400000000"),
                new BigDecimal("380000000"), new BigDecimal("20000000"), new BigDecimal("700000000"),
                new BigDecimal("1.20"), new BigDecimal("1.19"), 900_000_000L,
                new BigDecimal("500000000"), new BigDecimal("900000000"));
        FmpIncomeStatementEntry prior = new FmpIncomeStatementEntry(
                "O", "2024-12-31",
                new BigDecimal("900000000"), new BigDecimal("280000000"), new BigDecimal("380000000"),
                new BigDecimal("360000000"), new BigDecimal("18000000"), new BigDecimal("650000000"),
                new BigDecimal("1.10"), new BigDecimal("1.09"), 880_000_000L,
                new BigDecimal("470000000"), new BigDecimal("850000000"));

        FundamentalSnapshot snapshot = adapter.toFundamentalSnapshot(
                "O", List.of(latest, prior), List.of(), List.of(), null, new BigDecimal("55.00"));

        assertThat(snapshot.depreciationAndAmortizationHistory())
                .containsExactly(new BigDecimal("500000000"), new BigDecimal("470000000"));
        assertThat(snapshot.ebitdaHistory())
                .containsExactly(new BigDecimal("900000000"), new BigDecimal("850000000"));
    }

    @Test
    void toFundamentalSnapshot_missingIncomeHistory_returnsEmptyHistories() {
        FundamentalSnapshot snapshot = adapter.toFundamentalSnapshot(
                "O", List.of(), List.of(new FmpBalanceSheetEntry("O", "2025-12-31",
                        new BigDecimal("5000000000"), new BigDecimal("3000000000"), new BigDecimal("2000000000"),
                        new BigDecimal("2500000000"), new BigDecimal("100000000"))),
                List.of(new FmpCashFlowEntry("O", "2025-12-31",
                        new BigDecimal("600000000"), new BigDecimal("-50000000"), new BigDecimal("550000000"))),
                null, new BigDecimal("55.00"));

        assertThat(snapshot.depreciationAndAmortizationHistory()).isEmpty();
        assertThat(snapshot.ebitdaHistory()).isEmpty();
    }
}
