package it.mazzoni.vis.marketdata;

import it.mazzoni.vis.domain.entity.MarketDataFallbackEvent;
import it.mazzoni.vis.domain.repository.MarketDataFallbackEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class MarketDataFallbackRecorderTest {

    private final MarketDataFallbackEventRepository repository = mock(MarketDataFallbackEventRepository.class);
    private final MarketDataFallbackRecorder recorder = new MarketDataFallbackRecorder(repository);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void recordSafely_persistsNormalizedCorrelatedAndSanitizedEvent() {
        UUID runId = UUID.randomUUID();
        MDC.put("job.name", "quote-refresh");
        MDC.put("job.run.id", runId.toString());

        recorder.recordSafely(new FallbackEventCommand(
                "aapl", "quote", "primary_provider_fallback", "plan_restriction",
                "HTTP 402", "success", null, "price,volume",
                "apikey=secret\nprovider message", 14));

        ArgumentCaptor<MarketDataFallbackEvent> captor = ArgumentCaptor.forClass(MarketDataFallbackEvent.class);
        verify(repository).saveAndFlush(captor.capture());
        MarketDataFallbackEvent event = captor.getValue();
        assertThat(event.getSymbol()).isEqualTo("AAPL");
        assertThat(event.getOperation()).isEqualTo("QUOTE");
        assertThat(event.getEventType()).isEqualTo("PRIMARY_PROVIDER_FALLBACK");
        assertThat(event.getOutcome()).isEqualTo("SUCCESS");
        assertThat(event.getJobName()).isEqualTo("quote-refresh");
        assertThat(event.getJobRunId()).isEqualTo(runId);
        assertThat(event.getErrorDetail()).contains("apikey=[REDACTED]").doesNotContain("secret").doesNotContain("\n");
    }

    @Test
    void recordSafely_doesNotPropagatePersistenceFailure() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(repository).saveAndFlush(org.mockito.ArgumentMatchers.any());

        assertThatCode(() -> recorder.recordSafely(new FallbackEventCommand(
                "KO", "profile", "PRIMARY_PROVIDER_ENRICHMENT", "MISSING_FIELD",
                "SUCCESS_INCOMPLETE", "FAILED", "exchange", null,
                "Yahoo unavailable", 3)))
                .doesNotThrowAnyException();
    }
}
