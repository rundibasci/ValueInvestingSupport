package it.mazzoni.vis.jobs;

import it.mazzoni.vis.config.JobsProperties;
import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.exception.MarketDataUnavailableException;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.marketdata.fmp.dto.FmpStockListEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static it.mazzoni.vis.marketdata.MarketDataException.ErrorCode.SERVICE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkProfileSyncJobTest {

    private final MarketDataClient marketDataClient = mock(MarketDataClient.class);
    private final SecurityRepository securityRepository = mock(SecurityRepository.class);
    private final JobRunLogger jobRunLogger = mock(JobRunLogger.class);
    private final IngestionEventRecorder eventRecorder = mock(IngestionEventRecorder.class);

    @Test
    void execute_continuesAfterExchangeListFailureAndRecordsExchangeEvent() {
        BulkProfileSyncJob job = job("NYSE", "NASDAQ");
        RuntimeException timeout = new RuntimeException("ReadTimeoutException");
        when(marketDataClient.listSymbols("NYSE")).thenThrow(timeout);
        when(marketDataClient.listSymbols("NASDAQ")).thenReturn(List.of(entry("AAPL")));
        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(marketDataClient.getProfile("AAPL")).thenReturn(profile("AAPL"));

        int processed = job.execute();

        assertThat(processed).isEqualTo(1);
        verify(eventRecorder).failed("NYSE", "profile-list", timeout);
        verify(eventRecorder).success("AAPL", "profile");
        ArgumentCaptor<Security> saved = ArgumentCaptor.forClass(Security.class);
        verify(securityRepository).save(saved.capture());
        assertThat(saved.getValue().getSymbol()).isEqualTo("AAPL");
        assertThat(saved.getValue().getDescription()).isEqualTo("Apple designs consumer electronics.");
    }

    @Test
    void execute_throwsWhenEveryExchangeListFailsAfterRecordingEvents() {
        BulkProfileSyncJob job = job("NYSE", "NASDAQ");
        RuntimeException nyse = new RuntimeException("NYSE timeout");
        MarketDataException nasdaq = new MarketDataException(SERVICE_UNAVAILABLE, "NASDAQ");
        when(marketDataClient.listSymbols("NYSE")).thenThrow(nyse);
        when(marketDataClient.listSymbols("NASDAQ")).thenThrow(nasdaq);

        assertThatThrownBy(job::execute)
                .isSameAs(nyse);

        verify(eventRecorder).failed("NYSE", "profile-list", nyse);
        verify(eventRecorder).failed("NASDAQ", "profile-list", nasdaq);
    }

    @Test
    void execute_persistsSymbolAndContinuesWhenProfileRequestTimesOut() {
        BulkProfileSyncJob job = job("NASDAQ");
        FmpStockListEntry apple = entry("AAPL");
        MarketDataUnavailableException timeout = new MarketDataUnavailableException(
                "Yahoo Finance request timed out after 10 seconds");
        when(marketDataClient.listSymbols("NASDAQ")).thenReturn(List.of(apple));
        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
        when(marketDataClient.getProfile("AAPL")).thenThrow(timeout);

        int processed = job.execute();

        assertThat(processed).isEqualTo(1);
        verify(eventRecorder).failed("AAPL", "profile", timeout);
        ArgumentCaptor<Security> saved = ArgumentCaptor.forClass(Security.class);
        verify(securityRepository).save(saved.capture());
        assertThat(saved.getValue().getSymbol()).isEqualTo("AAPL");
        assertThat(saved.getValue().getExchange()).isEqualTo("NASDAQ");
    }

    private BulkProfileSyncJob job(String... exchanges) {
        return new BulkProfileSyncJob(
                marketDataClient,
                securityRepository,
                jobRunLogger,
                new JobsProperties(true, List.of(exchanges), Map.of(), true),
                eventRecorder
        );
    }

    private FmpStockListEntry entry(String symbol) {
        return new FmpStockListEntry(
                symbol,
                "Apple Inc.",
                "US",
                "Technology",
                "NASDAQ Global Select",
                "NASDAQ",
                "stock",
                new BigDecimal("182.50"),
                new BigDecimal("2800000000000"),
                1_000_000L,
                false,
                false
        );
    }

    private CompanyProfile profile(String symbol) {
        return new CompanyProfile(
                symbol,
                "Apple Inc.",
                "Technology",
                "Consumer Electronics",
                "US",
                "USD",
                "NASDAQ",
                new BigDecimal("2800000000000"),
                "Apple designs consumer electronics.",
                "https://www.apple.com"
        );
    }
}
