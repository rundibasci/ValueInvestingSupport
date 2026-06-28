package it.mazzoni.vis.marketdata.yahoo;

import it.mazzoni.vis.adapter.YahooFinanceAdapter;
import it.mazzoni.vis.client.yahoo.YahooFinanceClient;
import it.mazzoni.vis.client.yahoo.dto.*;
import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.exception.MarketDataUnavailableException;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.marketdata.MarketDataStatusTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YahooMarketDataClientTest {

    @Mock YahooFinanceClient yahooFinanceClient;
    @Mock YahooFinanceAdapter adapter;
    @Mock MarketDataStatusTracker statusTracker;
    @InjectMocks YahooMarketDataClient client;

    private static final String SYMBOL = "AAPL";

    private QuoteSummaryResponse stubQsr() {
        return new QuoteSummaryResponse(new QuoteSummaryData(List.of(new QuoteSummaryResult(
                null, null, null, null, null, null, null)), null));
    }

    private ChartResponse stubCr() {
        return new ChartResponse(new ChartData(List.of(
                new ChartResult(new ChartMeta(SYMBOL, "USD", 182.5, "Apple Inc.", "AAPL"))), null));
    }

    @Test
    void getQuote_returnsMappedPriceQuote() {
        ChartResponse cr = stubCr();
        when(yahooFinanceClient.getChart(SYMBOL)).thenReturn(cr);
        MarketPriceQuote expected = new MarketPriceQuote(SYMBOL, BigDecimal.valueOf(182.5), "USD", null, null);
        when(adapter.toPriceQuote(eq(SYMBOL), any())).thenReturn(expected);

        MarketPriceQuote result = client.getQuote(SYMBOL);

        assertThat(result.symbol()).isEqualTo(SYMBOL);
        assertThat(result.price()).isEqualByComparingTo("182.5");
    }

    @Test
    void getFundamentals_returnsMappedSnapshot() {
        QuoteSummaryResponse qsr = stubQsr();
        ChartResponse cr = stubCr();
        when(yahooFinanceClient.getQuoteSummary(SYMBOL)).thenReturn(qsr);
        when(yahooFinanceClient.getChart(SYMBOL)).thenReturn(cr);
        FundamentalSnapshot expected = new FundamentalSnapshot(
                SYMBOL, "Apple Inc.", "Technology", "Consumer Electronics",
                "US", "USD", BigDecimal.valueOf(182.5), BigDecimal.valueOf(6.13),
                BigDecimal.valueOf(3.95), 15_552_752_000L,
                List.of(), List.of(), List.of(), null, null, null);
        when(adapter.toFundamentalSnapshot(eq(SYMBOL), any(), any())).thenReturn(expected);

        FundamentalSnapshot result = client.getFundamentals(SYMBOL);

        assertThat(result.symbol()).isEqualTo(SYMBOL);
        assertThat(result.companyName()).isEqualTo("Apple Inc.");
    }

    @Test
    void getRatios_returnsMappedRatioSnapshot() {
        QuoteSummaryResponse qsr = stubQsr();
        when(yahooFinanceClient.getQuoteSummary(SYMBOL)).thenReturn(qsr);
        RatioSnapshot expected = new RatioSnapshot(
                SYMBOL, BigDecimal.valueOf(29.5), null, BigDecimal.valueOf(46.2),
                null, null, null, null, null, null, null, null);
        when(adapter.toRatioSnapshot(eq(SYMBOL), any())).thenReturn(expected);

        RatioSnapshot result = client.getRatios(SYMBOL);

        assertThat(result.symbol()).isEqualTo(SYMBOL);
        assertThat(result.peRatio()).isEqualByComparingTo("29.5");
    }

    @Test
    void getProfile_returnsMappedProfile() {
        QuoteSummaryResponse qsr = stubQsr();
        ChartResponse cr = stubCr();
        when(yahooFinanceClient.getQuoteSummary(SYMBOL)).thenReturn(qsr);
        when(yahooFinanceClient.getChart(SYMBOL)).thenReturn(cr);
        CompanyProfile expected = new CompanyProfile(
                SYMBOL, "Apple Inc.", "Technology", "Consumer Electronics",
                "US", "USD", null, BigDecimal.valueOf(2_800_000_000_000L));
        when(adapter.toCompanyProfile(eq(SYMBOL), any(), any())).thenReturn(expected);

        CompanyProfile result = client.getProfile(SYMBOL);

        assertThat(result.symbol()).isEqualTo(SYMBOL);
        assertThat(result.companyName()).isEqualTo("Apple Inc.");
    }

    @Test
    void getQuote_whenSymbolNotFound_throwsMarketDataExceptionNotFound() {
        when(yahooFinanceClient.getChart(SYMBOL)).thenThrow(new SymbolNotFoundException(SYMBOL));

        assertThatThrownBy(() -> client.getQuote(SYMBOL))
                .isInstanceOf(MarketDataException.class)
                .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                        .isEqualTo(MarketDataException.ErrorCode.NOT_FOUND));
    }

    @Test
    void getQuote_whenUnavailable_throwsMarketDataExceptionServiceUnavailable() {
        when(yahooFinanceClient.getChart(SYMBOL))
                .thenThrow(new MarketDataUnavailableException("Yahoo down"));

        assertThatThrownBy(() -> client.getQuote(SYMBOL))
                .isInstanceOf(MarketDataException.class)
                .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                        .isEqualTo(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE));
    }
}
