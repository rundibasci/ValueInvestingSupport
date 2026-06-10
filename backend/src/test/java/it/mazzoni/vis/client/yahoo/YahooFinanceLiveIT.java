package it.mazzoni.vis.client.yahoo;

import it.mazzoni.vis.client.yahoo.dto.ChartResponse;
import it.mazzoni.vis.client.yahoo.dto.QuoteSummaryResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the real Yahoo Finance API. Excluded from the default test run.
 * Run with: mvn verify -Dgroups=integration
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class YahooFinanceLiveIT {

    @Autowired
    private YahooFinanceClient client;

    @Test
    void fetchAaplQuoteSummaryReturnsValidData() {
        QuoteSummaryResponse response = client.getQuoteSummary("AAPL");

        assertThat(response.quoteSummary().result()).isNotEmpty();
        assertThat(response.quoteSummary().result().get(0).financialData().currentPrice().raw())
                .isPositive();
        assertThat(response.quoteSummary().result().get(0).incomeStatementHistory().entries())
                .isNotEmpty();
    }

    @Test
    void fetchAaplChartReturnsRegularMarketPrice() {
        ChartResponse response = client.getChart("AAPL");

        assertThat(response.chart().result()).isNotEmpty();
        assertThat(response.chart().result().get(0).meta().regularMarketPrice())
                .isPositive();
        assertThat(response.chart().result().get(0).meta().symbol())
                .isEqualToIgnoringCase("AAPL");
    }
}
