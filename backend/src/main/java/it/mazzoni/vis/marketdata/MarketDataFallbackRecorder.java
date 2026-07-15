package it.mazzoni.vis.marketdata;

import it.mazzoni.vis.domain.entity.MarketDataFallbackEvent;
import it.mazzoni.vis.domain.repository.MarketDataFallbackEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class MarketDataFallbackRecorder {

    private static final Logger log = LoggerFactory.getLogger(MarketDataFallbackRecorder.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final MarketDataFallbackEventRepository repository;

    public MarketDataFallbackRecorder(MarketDataFallbackEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSafely(FallbackEventCommand command) {
        try {
            MarketDataFallbackEvent event = new MarketDataFallbackEvent();
            event.setJobRunId(currentRunId());
            event.setJobName(normalizeNullable(MDC.get("job.name"), 100));
            event.setSymbol(normalizeRequired(command.symbol(), 20));
            event.setOperation(normalizeRequired(command.operation(), 40));
            event.setEventType(normalizeRequired(command.eventType(), 50));
            event.setTriggerReason(normalizeRequired(command.triggerReason(), 50));
            event.setPrimaryProvider("FMP");
            event.setFallbackProvider("YAHOO");
            event.setPrimaryStatus(normalizeNullable(command.primaryStatus(), 80));
            event.setOutcome(normalizeRequired(command.outcome(), 20));
            event.setMissingFields(normalizeNullable(command.missingFields(), 500));
            event.setAcceptedFields(normalizeNullable(command.acceptedFields(), 500));
            event.setErrorDetail(sanitize(command.errorDetail()));
            event.setDurationMs(Math.max(0, command.durationMs()));
            event.setOccurredAt(LocalDateTime.now());
            repository.saveAndFlush(event);
        } catch (RuntimeException e) {
            log.warn("market_data_fallback_event_persistence_failed symbol={} operation={} message={}",
                    command.symbol(), command.operation(), sanitize(e.getMessage()));
        }
    }

    private UUID currentRunId() {
        String value = MDC.get("job.run.id");
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizeRequired(String value, int maxLength) {
        String normalized = value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
        return limit(normalized.isBlank() ? "UNKNOWN" : normalized, maxLength);
    }

    private String normalizeNullable(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        return limit(value.trim(), maxLength);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) return null;
        String sanitized = value
                .replaceAll("(?i)(apikey|api_key|authorization|cookie|crumb)\\s*[=:]\\s*[^,;\\s]+", "$1=[REDACTED]")
                .replaceAll("[\\r\\n\\t]+", " ");
        return limit(sanitized, MAX_ERROR_LENGTH);
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
