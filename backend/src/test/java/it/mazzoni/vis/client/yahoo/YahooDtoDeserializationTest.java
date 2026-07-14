package it.mazzoni.vis.client.yahoo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.mazzoni.vis.client.yahoo.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class YahooDtoDeserializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private <T> T load(String fixture, Class<T> type) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/yahoo/" + fixture)) {
            return objectMapper.readValue(is, type);
        }
    }

    @Test
    void quoteSummaryDeserializesTopLevelStructure() throws Exception {
        QuoteSummaryResponse response = load("aapl_quotesummary.json", QuoteSummaryResponse.class);

        assertThat(response.quoteSummary()).isNotNull();
        assertThat(response.quoteSummary().result()).hasSize(1);
        assertThat(response.quoteSummary().error()).isNull();
    }

    @Test
    void financialDataFieldsDeserialize() throws Exception {
        QuoteSummaryResponse response = load("aapl_quotesummary.json", QuoteSummaryResponse.class);
        FinancialDataDto fd = response.quoteSummary().result().get(0).financialData();

        assertThat(fd).isNotNull();
        assertThat(fd.currentPrice().raw()).isEqualTo(182.5);
        assertThat(fd.totalDebt().raw()).isEqualTo(110_000_000_000.0);
        assertThat(fd.returnOnEquity().raw()).isEqualTo(1.47);
        assertThat(fd.currentRatio().raw()).isEqualTo(0.988);
    }

    @Test
    void defaultKeyStatisticsDeserializes() throws Exception {
        QuoteSummaryResponse response = load("aapl_quotesummary.json", QuoteSummaryResponse.class);
        DefaultKeyStatisticsDto ks = response.quoteSummary().result().get(0).defaultKeyStatistics();

        assertThat(ks).isNotNull();
        assertThat(ks.trailingEps().raw()).isEqualTo(6.13);
        assertThat(ks.bookValue().raw()).isEqualTo(3.95);
        assertThat(ks.sharesOutstanding().raw()).isEqualTo(15_700_000_000.0);
    }

    @Test
    void incomeStatementHistoryDeserializesAllEntries() throws Exception {
        QuoteSummaryResponse response = load("aapl_quotesummary.json", QuoteSummaryResponse.class);
        IncomeStatementHistoryDto hist =
                response.quoteSummary().result().get(0).incomeStatementHistory();

        assertThat(hist).isNotNull();
        assertThat(hist.entries()).hasSize(4);
        assertThat(hist.entries().get(0).totalRevenue().raw()).isEqualTo(383_285_000_000.0);
        assertThat(hist.entries().get(0).netIncome().raw()).isEqualTo(96_995_000_000.0);
    }

    @Test
    void cashflowHistoryDeserializesCapexAsNegative() throws Exception {
        QuoteSummaryResponse response = load("aapl_quotesummary.json", QuoteSummaryResponse.class);
        CashflowStatementHistoryDto cfHist =
                response.quoteSummary().result().get(0).cashflowStatementHistory();

        assertThat(cfHist).isNotNull();
        assertThat(cfHist.entries()).hasSize(4);
        assertThat(cfHist.entries().get(0).totalCashFromOperatingActivities().raw())
                .isEqualTo(110_543_000_000.0);
        assertThat(cfHist.entries().get(0).capitalExpenditures().raw())
                .isNegative();
    }

    @Test
    void balanceSheetDeserializes() throws Exception {
        QuoteSummaryResponse response = load("aapl_quotesummary.json", QuoteSummaryResponse.class);
        BalanceSheetHistoryDto bsHist =
                response.quoteSummary().result().get(0).balanceSheetHistory();

        assertThat(bsHist).isNotNull();
        assertThat(bsHist.entries()).hasSize(2);
        assertThat(bsHist.entries().get(0).totalStockholderEquity().raw())
                .isEqualTo(62_146_000_000.0);
    }

    @Test
    void summaryDetailDeserializes() throws Exception {
        QuoteSummaryResponse response = load("aapl_quotesummary.json", QuoteSummaryResponse.class);
        SummaryDetailDto sd = response.quoteSummary().result().get(0).summaryDetail();

        assertThat(sd).isNotNull();
        assertThat(sd.trailingPE().raw()).isEqualTo(29.86);
        assertThat(sd.dividendYield().raw()).isEqualTo(0.0053);
        assertThat(sd.currency()).isEqualTo("USD");
    }

    @Test
    void assetProfileDeserializes() throws Exception {
        QuoteSummaryResponse response = load("aapl_quotesummary.json", QuoteSummaryResponse.class);
        AssetProfileDto ap = response.quoteSummary().result().get(0).assetProfile();

        assertThat(ap).isNotNull();
        assertThat(ap.sector()).isEqualTo("Technology");
        assertThat(ap.industry()).isEqualTo("Consumer Electronics");
        assertThat(ap.country()).isEqualTo("United States");
    }

    @Test
    void chartResponseDeserializes() throws Exception {
        ChartResponse response = load("aapl_chart.json", ChartResponse.class);

        assertThat(response.chart()).isNotNull();
        assertThat(response.chart().result()).hasSize(1);
        ChartMeta meta = response.chart().result().get(0).meta();
        assertThat(meta.symbol()).isEqualTo("AAPL");
        assertThat(meta.regularMarketPrice()).isEqualTo(182.5);
        assertThat(meta.longName()).isEqualTo("Apple Inc.");
        assertThat(meta.currency()).isEqualTo("USD");
        assertThat(meta.exchangeName()).isEqualTo("NMS");
        assertThat(response.chart().result().get(0).indicators().quote().get(0).volume())
                .containsExactly(48_000_000L);
    }
}
