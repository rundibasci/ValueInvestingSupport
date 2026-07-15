package it.mazzoni.vis.marketdata;

public record FallbackEventCommand(
        String symbol,
        String operation,
        String eventType,
        String triggerReason,
        String primaryStatus,
        String outcome,
        String missingFields,
        String acceptedFields,
        String errorDetail,
        long durationMs
) {
}

