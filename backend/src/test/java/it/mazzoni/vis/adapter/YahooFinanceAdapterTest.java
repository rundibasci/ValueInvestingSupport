package it.mazzoni.vis.adapter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.mazzoni.vis.client.yahoo.dto.ChartResponse;
import it.mazzoni.vis.client.yahoo.dto.QuoteSummaryResponse;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.RatioSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class YahooFinanceAdapterTest {

    private ObjectMapper objectMapper;
    private YahooFinanceAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        adapter = new YahooFinanceAdapter();
    }

    private QuoteSummaryResponse loadQsr() throws Exception {
        try (InputStream is = getClass().getResourceAsStream(
                "/fixtures/yahoo/aapl_quotesummary.json")) {
            return objectMapper.readValue(is, QuoteSummaryResponse.class);
        }
    }

    private ChartResponse loadChart() throws Exception {
        try (InputStream is = getClass().getResourceAsStream(
                "/fixtures/yahoo/aapl_chart.json")) {
            return objectMapper.readValue(is, ChartResponse.class);
        }
    }

    @Test
    void fundamentalSnapshotSymbolAndCompanyName() throws Exception {
        FundamentalSnapshot snap = adapter.toFundamentalSnapshot("AAPL", loadQsr(), loadChart());
        assertThat(snap.symbol()).isEqualTo("AAPL");
        assertThat(snap.companyName()).isEqualTo("Apple Inc.");
    }

    @Test
    void currentPriceComesFromChart() throws Exception {
        FundamentalSnapshot snap = adapter.toFundamentalSnapshot("AAPL", loadQsr(), loadChart());
        assertThat(snap.currentPrice()).isEqualByComparingTo("182.5");
    }

    @Test
    void epsAndBookValueMapped() throws Exception {
        FundamentalSnapshot snap = adapter.toFundamentalSnapshot("AAPL", loadQsr(), loadChart());
        assertThat(snap.epsTtm()).isEqualByComparingTo("6.13");
        assertThat(snap.bookValuePerShare()).isEqualByComparingTo("3.95");
    }

    @Test
    void revenueHistoryHasFourEntries() throws Exception {
        FundamentalSnapshot snap = adapter.toFundamentalSnapshot("AAPL", loadQsr(), loadChart());
        assertThat(snap.revenueHistory()).hasSize(4);
        assertThat(snap.revenueHistory().get(0))
                .isEqualByComparingTo(new BigDecimal("383285000000"));
    }

    @Test
    void fcfHistoryHasFourEntriesAndIsPositive() throws Exception {
        FundamentalSnapshot snap = adapter.toFundamentalSnapshot("AAPL", loadQsr(), loadChart());
        assertThat(snap.fcfHistory()).hasSize(4);
        // FCF = opCF + capex = 110_543_000_000 + (-10_959_000_000) = 99_584_000_000
        assertThat(snap.fcfHistory().get(0))
                .isEqualByComparingTo(new BigDecimal("99584000000"));
        assertThat(snap.fcfHistory()).allMatch(v -> v.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void netDebtComputed() throws Exception {
        FundamentalSnapshot snap = adapter.toFundamentalSnapshot("AAPL", loadQsr(), loadChart());
        // netDebt = totalDebt(110B) - cash(30.737B)
        assertThat(snap.netDebt()).isEqualByComparingTo(
                new BigDecimal("110000000000").subtract(new BigDecimal("30737000000")));
    }

    @Test
    void sectorAndCountryMapped() throws Exception {
        FundamentalSnapshot snap = adapter.toFundamentalSnapshot("AAPL", loadQsr(), loadChart());
        assertThat(snap.sector()).isEqualTo("Technology");
        assertThat(snap.country()).isEqualTo("United States");
        assertThat(snap.currency()).isEqualTo("USD");
    }

    @Test
    void ratioSnapshotPeRatioMatchesSummaryDetail() throws Exception {
        RatioSnapshot ratio = adapter.toRatioSnapshot("AAPL", loadQsr());
        assertThat(ratio.peRatio()).isEqualByComparingTo("29.86");
        assertThat(ratio.roe()).isEqualByComparingTo("1.47");
        assertThat(ratio.dividendYield()).isEqualByComparingTo("0.0053");
    }

    @Test
    void roicComputedFromBalanceSheetAndIncome() throws Exception {
        RatioSnapshot ratio = adapter.toRatioSnapshot("AAPL", loadQsr());
        // ROIC = netIncome(96_995M) / (equity(62_146M) + longTermDebt(95_281M))
        assertThat(ratio.roic()).isNotNull();
        assertThat(ratio.roic()).isPositive();
    }

    @Test
    void nullCashflowHistoryProducesEmptyFcfList() throws Exception {
        String json;
        try (InputStream is = getClass().getResourceAsStream(
                "/fixtures/yahoo/aapl_quotesummary.json")) {
            json = new String(is.readAllBytes());
        }
        ObjectNode root = (ObjectNode) objectMapper.readTree(json);
        ObjectNode resultNode = (ObjectNode) root
                .path("quoteSummary").path("result").get(0);
        resultNode.remove("cashflowStatementHistory");

        QuoteSummaryResponse qsr = objectMapper.treeToValue(root, QuoteSummaryResponse.class);
        FundamentalSnapshot snap = adapter.toFundamentalSnapshot("AAPL", qsr, null);

        assertThat(snap.fcfHistory()).isEmpty();
    }

    @Test
    void nullChartFallsBackToFinancialDataCurrentPrice() throws Exception {
        FundamentalSnapshot snap = adapter.toFundamentalSnapshot("AAPL", loadQsr(), null);
        assertThat(snap.currentPrice()).isEqualByComparingTo("182.5");
    }
}
