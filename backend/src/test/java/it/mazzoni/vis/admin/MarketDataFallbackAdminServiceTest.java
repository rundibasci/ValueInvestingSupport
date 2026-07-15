package it.mazzoni.vis.admin;

import it.mazzoni.vis.domain.entity.MarketDataFallbackEvent;
import it.mazzoni.vis.domain.repository.MarketDataFallbackEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataFallbackAdminServiceTest {

    @Mock MarketDataFallbackEventRepository repository;

    @Test
    void summary_separatesFallbackEnrichmentFailureAndRejection() {
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(
                event("KO", "PROFILE", "PRIMARY_PROVIDER_FALLBACK", "PLAN_RESTRICTION", "SUCCESS"),
                event("KO", "QUOTE", "PRIMARY_PROVIDER_ENRICHMENT", "MISSING_FIELD", "SUCCESS"),
                event("IBM", "RATIOS", "PRIMARY_PROVIDER_FALLBACK", "PLAN_RESTRICTION", "FAILED"),
                event("IBM", "QUOTE", "PRIMARY_PROVIDER_ENRICHMENT", "MISSING_FIELD", "REJECTED")
        ));

        MarketDataFallbackSummaryResponse result = new MarketDataFallbackAdminService(repository)
                .summary(null, null, null, null, null, null, null, null);

        assertThat(result.totalAttempts()).isEqualTo(4);
        assertThat(result.successfulFallbacks()).isEqualTo(1);
        assertThat(result.successfulEnrichments()).isEqualTo(1);
        assertThat(result.failedAttempts()).isEqualTo(1);
        assertThat(result.rejectedAttempts()).isEqualTo(1);
        assertThat(result.affectedSymbols()).isEqualTo(2);
        assertThat(result.byTrigger()).containsEntry("PLAN_RESTRICTION", 2L);
    }

    @Test
    void events_mapsPersistentFieldsToAdminResponse() {
        MarketDataFallbackEvent event = event("KO", "PROFILE", "PRIMARY_PROVIDER_ENRICHMENT", "MISSING_FIELD", "SUCCESS");
        event.setAcceptedFields("exchange");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        PageResponse<MarketDataFallbackEventResponse> result = new MarketDataFallbackAdminService(repository)
                .events("ko", null, null, null, null, null, null, null, 0, 50);

        assertThat(result.content()).singleElement().satisfies(response -> {
            assertThat(response.symbol()).isEqualTo("KO");
            assertThat(response.acceptedFields()).isEqualTo("exchange");
            assertThat(response.eventType()).isEqualTo("PRIMARY_PROVIDER_ENRICHMENT");
        });
    }

    private MarketDataFallbackEvent event(String symbol, String operation, String type, String trigger, String outcome) {
        MarketDataFallbackEvent event = new MarketDataFallbackEvent();
        event.setSymbol(symbol);
        event.setOperation(operation);
        event.setEventType(type);
        event.setTriggerReason(trigger);
        event.setPrimaryProvider("FMP");
        event.setFallbackProvider("YAHOO");
        event.setOutcome(outcome);
        event.setOccurredAt(LocalDateTime.now());
        return event;
    }
}
