package it.mazzoni.vis.marketdata;

import it.mazzoni.vis.adapter.YahooFinanceAdapter;
import it.mazzoni.vis.client.yahoo.YahooFinanceClient;
import it.mazzoni.vis.client.yahoo.dto.*;
import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.exception.MarketDataUnavailableException;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.observability.ObservabilitySupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FmpWithYahooFallbackMarketDataClientTest {

    @Mock MarketDataClient fmpClient;
    @Mock YahooFinanceClient yahooClient;
    @Mock YahooFinanceAdapter yahooAdapter;
    @Mock SourceTracker sourceTracker;
    @Mock MarketDataFallbackRecorder fallbackRecorder;

    FmpWithYahooFallbackMarketDataClient client;

    private static final String SYMBOL = "AAPL";

    @BeforeEach
    void setUp() {
        client = new FmpWithYahooFallbackMarketDataClient(
                fmpClient,
                yahooClient,
                yahooAdapter,
                sourceTracker,
                new MarketDataStatusTracker(),
                new ObservabilitySupport(new SimpleMeterRegistry()),
                fallbackRecorder);
    }

    // --- shared stubs ---

    private static CompanyProfile profile() {
        return new CompanyProfile(SYMBOL, "Apple Inc.", "Technology", "Consumer Electronics",
                "US", "USD", "NASDAQ", BigDecimal.valueOf(2_800_000_000_000L),
                "Apple designs consumer electronics and software.", "https://www.apple.com");
    }

    private static FundamentalSnapshot fundamentals() {
        return new FundamentalSnapshot(SYMBOL, "Apple Inc.", "Technology", "Consumer Electronics",
                "US", "USD", BigDecimal.valueOf(185), BigDecimal.valueOf(6.13),
                BigDecimal.valueOf(4.0), 15_000_000_000L,
                List.of(BigDecimal.valueOf(400_000_000_000L)), List.of(), List.of(),
                null, null, null);
    }

    private static RatioSnapshot ratios() {
        return new RatioSnapshot(SYMBOL, BigDecimal.valueOf(30), null, BigDecimal.valueOf(45),
                null, null, null, null, null, null, null, null);
    }

    private static MarketPriceQuote quote() {
        return new MarketPriceQuote(SYMBOL, BigDecimal.valueOf(185), "USD", null, null, 1_000_000L);
    }

    private static QuoteSummaryResponse stubQsr() {
        return new QuoteSummaryResponse(new QuoteSummaryData(List.of(new QuoteSummaryResult(
                null, null, null, null, null, null, null)), null));
    }

    private static ChartResponse stubCr() {
        return new ChartResponse(new ChartData(List.of(
                new ChartResult(new ChartMeta(SYMBOL, "USD", 185.0, "Apple Inc.", SYMBOL, "NMS"), null)), null));
    }

    private static MarketDataException planRestriction() {
        return new MarketDataException(MarketDataException.ErrorCode.PLAN_RESTRICTION, SYMBOL);
    }

    private static MarketDataException notFound() {
        return new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, SYMBOL);
    }

    // -------------------------------------------------------------------------
    // getProfile
    // -------------------------------------------------------------------------

    @Nested
    class GetProfile {

        @Test
        void whenFmpSucceeds_returnsFmpResult() {
            CompanyProfile expected = profile();
            when(fmpClient.getProfile(SYMBOL)).thenReturn(expected);

            CompanyProfile result = client.getProfile(SYMBOL);

            assertThat(result).isSameAs(expected);
            verify(sourceTracker).record("FMP");
            verifyNoInteractions(yahooClient, yahooAdapter);
        }

        @Test
        void whenFmpPlanRestriction_callsYahooFallback() {
            when(fmpClient.getProfile(SYMBOL)).thenThrow(planRestriction());
            QuoteSummaryResponse qsr = stubQsr();
            ChartResponse cr = stubCr();
            when(yahooClient.getQuoteSummary(SYMBOL)).thenReturn(qsr);
            when(yahooClient.getChart(SYMBOL)).thenReturn(cr);
            CompanyProfile expected = profile();
            when(yahooAdapter.toCompanyProfile(eq(SYMBOL), any(), any())).thenReturn(expected);

            CompanyProfile result = client.getProfile(SYMBOL);

            assertThat(result).isSameAs(expected);
            verify(sourceTracker).record("Yahoo");
            verify(fallbackRecorder).recordSafely(argThat(event ->
                    "PRIMARY_PROVIDER_FALLBACK".equals(event.eventType())
                            && "SUCCESS".equals(event.outcome())
                            && "PLAN_RESTRICTION".equals(event.triggerReason())));
        }

        @Test
        void whenFmpNotFound_rethrowsWithoutCallingYahoo() {
            when(fmpClient.getProfile(SYMBOL)).thenThrow(notFound());

            assertThatThrownBy(() -> client.getProfile(SYMBOL))
                    .isInstanceOf(MarketDataException.class)
                    .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                            .isEqualTo(MarketDataException.ErrorCode.NOT_FOUND));

            verifyNoInteractions(yahooClient);
        }

        @Test
        void whenYahooThrowsSymbolNotFound_wrapsToNotFound() {
            when(fmpClient.getProfile(SYMBOL)).thenThrow(planRestriction());
            when(yahooClient.getQuoteSummary(SYMBOL)).thenThrow(new SymbolNotFoundException(SYMBOL));

            assertThatThrownBy(() -> client.getProfile(SYMBOL))
                    .isInstanceOf(MarketDataException.class)
                    .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                            .isEqualTo(MarketDataException.ErrorCode.NOT_FOUND));
        }

        @Test
        void whenYahooThrowsUnavailable_wrapsToServiceUnavailable() {
            when(fmpClient.getProfile(SYMBOL)).thenThrow(planRestriction());
            when(yahooClient.getQuoteSummary(SYMBOL)).thenThrow(new MarketDataUnavailableException("Yahoo down"));

            assertThatThrownBy(() -> client.getProfile(SYMBOL))
                    .isInstanceOf(MarketDataException.class)
                    .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                            .isEqualTo(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE));
            verify(fallbackRecorder).recordSafely(argThat(event ->
                    "FAILED".equals(event.outcome()) && "profile".equals(event.operation())));
        }

        @Test
        void whenFmpProfileMissesExchange_recordsAcceptedYahooEnrichment() {
            CompanyProfile incomplete = new CompanyProfile(SYMBOL, "Apple Inc.", "Technology", "Electronics",
                    "US", "USD", null, BigDecimal.TEN, "Description", "https://apple.com");
            when(fmpClient.getProfile(SYMBOL)).thenReturn(incomplete);
            when(yahooClient.getQuoteSummary(SYMBOL)).thenReturn(stubQsr());
            when(yahooClient.getChart(SYMBOL)).thenReturn(stubCr());
            when(yahooAdapter.toCompanyProfile(eq(SYMBOL), any(), any())).thenReturn(profile());

            CompanyProfile result = client.getProfile(SYMBOL);

            assertThat(result.exchange()).isEqualTo("NASDAQ");
            verify(fallbackRecorder).recordSafely(argThat(event ->
                    "PRIMARY_PROVIDER_ENRICHMENT".equals(event.eventType())
                            && "SUCCESS".equals(event.outcome())
                            && "exchange".equals(event.acceptedFields())));
        }
    }

    // -------------------------------------------------------------------------
    // getFundamentals
    // -------------------------------------------------------------------------

    @Nested
    class GetFundamentals {

        @Test
        void whenFmpSucceeds_returnsFmpResult() {
            FundamentalSnapshot expected = fundamentals();
            when(fmpClient.getFundamentals(SYMBOL)).thenReturn(expected);

            FundamentalSnapshot result = client.getFundamentals(SYMBOL);

            assertThat(result).isSameAs(expected);
            verify(sourceTracker).record("FMP");
            verifyNoInteractions(yahooClient, yahooAdapter);
        }

        @Test
        void whenFmpPlanRestriction_callsYahooFallback() {
            when(fmpClient.getFundamentals(SYMBOL)).thenThrow(planRestriction());
            QuoteSummaryResponse qsr = stubQsr();
            ChartResponse cr = stubCr();
            when(yahooClient.getQuoteSummary(SYMBOL)).thenReturn(qsr);
            when(yahooClient.getChart(SYMBOL)).thenReturn(cr);
            FundamentalSnapshot expected = fundamentals();
            when(yahooAdapter.toFundamentalSnapshot(eq(SYMBOL), any(), any())).thenReturn(expected);

            FundamentalSnapshot result = client.getFundamentals(SYMBOL);

            assertThat(result).isSameAs(expected);
            verify(sourceTracker).record("Yahoo");
        }

        @Test
        void whenFmpServiceUnavailable_rethrowsWithoutCallingYahoo() {
            when(fmpClient.getFundamentals(SYMBOL)).thenThrow(
                    new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, SYMBOL));

            assertThatThrownBy(() -> client.getFundamentals(SYMBOL))
                    .isInstanceOf(MarketDataException.class)
                    .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                            .isEqualTo(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE));

            verifyNoInteractions(yahooClient);
        }

        @Test
        void whenYahooThrowsSymbolNotFound_wrapsToNotFound() {
            when(fmpClient.getFundamentals(SYMBOL)).thenThrow(planRestriction());
            when(yahooClient.getQuoteSummary(SYMBOL)).thenThrow(new SymbolNotFoundException(SYMBOL));

            assertThatThrownBy(() -> client.getFundamentals(SYMBOL))
                    .isInstanceOf(MarketDataException.class)
                    .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                            .isEqualTo(MarketDataException.ErrorCode.NOT_FOUND));
        }
    }

    // -------------------------------------------------------------------------
    // getRatios
    // -------------------------------------------------------------------------

    @Nested
    class GetRatios {

        @Test
        void whenFmpSucceeds_returnsFmpResult() {
            RatioSnapshot expected = ratios();
            when(fmpClient.getRatios(SYMBOL)).thenReturn(expected);

            RatioSnapshot result = client.getRatios(SYMBOL);

            assertThat(result).isSameAs(expected);
            verify(sourceTracker).record("FMP");
            verifyNoInteractions(yahooClient, yahooAdapter);
        }

        @Test
        void whenFmpPlanRestriction_callsYahooFallback() {
            when(fmpClient.getRatios(SYMBOL)).thenThrow(planRestriction());
            QuoteSummaryResponse qsr = stubQsr();
            when(yahooClient.getQuoteSummary(SYMBOL)).thenReturn(qsr);
            RatioSnapshot expected = ratios();
            when(yahooAdapter.toRatioSnapshot(eq(SYMBOL), any())).thenReturn(expected);

            RatioSnapshot result = client.getRatios(SYMBOL);

            assertThat(result).isSameAs(expected);
            verify(sourceTracker).record("Yahoo");
            // getRatios only calls getQuoteSummary, not getChart
            verify(yahooClient, never()).getChart(any());
        }

        @Test
        void whenFmpNotFound_rethrowsWithoutCallingYahoo() {
            when(fmpClient.getRatios(SYMBOL)).thenThrow(notFound());

            assertThatThrownBy(() -> client.getRatios(SYMBOL))
                    .isInstanceOf(MarketDataException.class)
                    .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                            .isEqualTo(MarketDataException.ErrorCode.NOT_FOUND));

            verifyNoInteractions(yahooClient);
        }

        @Test
        void whenYahooThrowsUnavailable_wrapsToServiceUnavailable() {
            when(fmpClient.getRatios(SYMBOL)).thenThrow(planRestriction());
            when(yahooClient.getQuoteSummary(SYMBOL)).thenThrow(new MarketDataUnavailableException("Yahoo down"));

            assertThatThrownBy(() -> client.getRatios(SYMBOL))
                    .isInstanceOf(MarketDataException.class)
                    .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                            .isEqualTo(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE));
        }
    }

    // -------------------------------------------------------------------------
    // getAnnualRatios
    // -------------------------------------------------------------------------

    @Nested
    class GetAnnualRatios {

        @Test
        void whenFmpSucceeds_returnsFullFmpHistory() {
            List<RatioSnapshot> expected = List.of(ratios(), ratios());
            when(fmpClient.getAnnualRatios(SYMBOL)).thenReturn(expected);

            List<RatioSnapshot> result = client.getAnnualRatios(SYMBOL);

            assertThat(result).isSameAs(expected);
            verify(sourceTracker).record("FMP");
            verifyNoInteractions(yahooClient, yahooAdapter);
            verify(fmpClient, never()).getRatios(SYMBOL);
        }

        @Test
        void whenFmpPlanRestriction_fallsBackToSingleYahooSnapshot() {
            when(fmpClient.getAnnualRatios(SYMBOL)).thenThrow(planRestriction());
            when(fmpClient.getRatios(SYMBOL)).thenThrow(planRestriction());
            QuoteSummaryResponse qsr = stubQsr();
            when(yahooClient.getQuoteSummary(SYMBOL)).thenReturn(qsr);
            RatioSnapshot expected = ratios();
            when(yahooAdapter.toRatioSnapshot(eq(SYMBOL), any())).thenReturn(expected);

            List<RatioSnapshot> result = client.getAnnualRatios(SYMBOL);

            assertThat(result).containsExactly(expected);
            verify(sourceTracker).record("Yahoo");
        }

        @Test
        void whenFmpNotFound_rethrowsWithoutCallingYahoo() {
            when(fmpClient.getAnnualRatios(SYMBOL)).thenThrow(notFound());

            assertThatThrownBy(() -> client.getAnnualRatios(SYMBOL))
                    .isInstanceOf(MarketDataException.class)
                    .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                            .isEqualTo(MarketDataException.ErrorCode.NOT_FOUND));

            verifyNoInteractions(yahooClient);
        }
    }

    // -------------------------------------------------------------------------
    // getQuote
    // -------------------------------------------------------------------------

    @Nested
    class GetQuote {

        @Test
        void whenFmpSucceeds_returnsFmpResult() {
            MarketPriceQuote expected = quote();
            when(fmpClient.getQuote(SYMBOL)).thenReturn(expected);

            MarketPriceQuote result = client.getQuote(SYMBOL);

            assertThat(result).isSameAs(expected);
            verify(sourceTracker).record("FMP");
            verifyNoInteractions(yahooClient, yahooAdapter);
        }

        @Test
        void whenFmpPlanRestriction_callsYahooFallback() {
            when(fmpClient.getQuote(SYMBOL)).thenThrow(planRestriction());
            ChartResponse cr = stubCr();
            when(yahooClient.getChart(SYMBOL)).thenReturn(cr);
            MarketPriceQuote expected = quote();
            when(yahooAdapter.toPriceQuote(eq(SYMBOL), any())).thenReturn(expected);

            MarketPriceQuote result = client.getQuote(SYMBOL);

            assertThat(result.price()).isEqualByComparingTo("185");
            verify(sourceTracker).record("Yahoo");
            verify(fallbackRecorder).recordSafely(argThat(event ->
                    "PRIMARY_PROVIDER_FALLBACK".equals(event.eventType())
                            && "SUCCESS".equals(event.outcome())
                            && event.acceptedFields().contains("price")));
            // getQuote only calls getChart, not getQuoteSummary
            verify(yahooClient, never()).getQuoteSummary(any());
        }

        @Test
        void whenFmpNotFound_rethrowsWithoutCallingYahoo() {
            when(fmpClient.getQuote(SYMBOL)).thenThrow(notFound());

            assertThatThrownBy(() -> client.getQuote(SYMBOL))
                    .isInstanceOf(MarketDataException.class)
                    .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                            .isEqualTo(MarketDataException.ErrorCode.NOT_FOUND));

            verifyNoInteractions(yahooClient);
        }

        @Test
        void whenYahooThrowsSymbolNotFound_wrapsToNotFound() {
            when(fmpClient.getQuote(SYMBOL)).thenThrow(planRestriction());
            when(yahooClient.getChart(SYMBOL)).thenThrow(new SymbolNotFoundException(SYMBOL));

            assertThatThrownBy(() -> client.getQuote(SYMBOL))
                    .isInstanceOf(MarketDataException.class)
                    .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                            .isEqualTo(MarketDataException.ErrorCode.NOT_FOUND));
        }

        @Test
        void whenYahooThrowsUnavailable_wrapsToServiceUnavailable() {
            when(fmpClient.getQuote(SYMBOL)).thenThrow(planRestriction());
            when(yahooClient.getChart(SYMBOL)).thenThrow(new MarketDataUnavailableException("Yahoo down"));

            assertThatThrownBy(() -> client.getQuote(SYMBOL))
                    .isInstanceOf(MarketDataException.class)
                    .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                            .isEqualTo(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE));
        }

        @Test
        void whenFmpQuoteMissesVolume_recordsRejectedYahooEnrichment() {
            MarketPriceQuote incomplete = new MarketPriceQuote(SYMBOL, BigDecimal.valueOf(185), "USD", null, null, null);
            when(fmpClient.getQuote(SYMBOL)).thenReturn(incomplete);
            when(yahooClient.getChart(SYMBOL)).thenReturn(stubCr());
            when(yahooAdapter.toPriceQuote(eq(SYMBOL), any())).thenReturn(
                    new MarketPriceQuote(SYMBOL, BigDecimal.valueOf(185), "USD", null, null, null));

            MarketPriceQuote result = client.getQuote(SYMBOL);

            assertThat(result).isSameAs(incomplete);
            verify(fallbackRecorder).recordSafely(argThat(event ->
                    "PRIMARY_PROVIDER_ENRICHMENT".equals(event.eventType())
                            && "REJECTED".equals(event.outcome())
                            && "volume".equals(event.missingFields())));
        }
    }
}
