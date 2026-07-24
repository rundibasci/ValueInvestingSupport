package it.mazzoni.vis.client.yahoo;

import it.mazzoni.vis.exception.MarketDataUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class YahooFinanceClientImplTest {

    private final YahooFinanceClientImpl client = new YahooFinanceClientImpl(
            WebClient.builder().baseUrl("http://localhost").build(),
            mock(YahooCrumbProvider.class));

    @Test
    void translateBlockingTimeout_mapsReactorBlockingTimeoutToDomainException() {
        IllegalStateException blockingTimeout = new IllegalStateException(
                "Timeout on blocking read for 10000000000 NANOSECONDS",
                new TimeoutException("Timeout on blocking read"));

        RuntimeException translated = client.translateBlockingTimeout(blockingTimeout);

        assertThat(translated)
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessage("Yahoo Finance request timed out after 10 seconds")
                .hasCause(blockingTimeout);
    }

    @Test
    void translateBlockingTimeout_preservesUnrelatedIllegalStateException() {
        IllegalStateException unrelated = new IllegalStateException("unexpected state");

        assertThat(client.translateBlockingTimeout(unrelated)).isSameAs(unrelated);
    }
}
