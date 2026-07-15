package it.mazzoni.vis.admin;

import java.time.LocalDateTime;
import java.util.UUID;

public record MarketDataFallbackEventResponse(
        UUID id,
        UUID jobRunId,
        String jobName,
        String symbol,
        String operation,
        String eventType,
        String triggerReason,
        String primaryProvider,
        String fallbackProvider,
        String primaryStatus,
        String outcome,
        String missingFields,
        String acceptedFields,
        String errorDetail,
        long durationMs,
        LocalDateTime occurredAt
) {
}

