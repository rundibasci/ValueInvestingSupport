package it.mazzoni.vis.admin;

import java.time.LocalDateTime;
import java.util.Map;

public record MarketDataFallbackSummaryResponse(
        long totalAttempts,
        long successfulFallbacks,
        long successfulEnrichments,
        long failedAttempts,
        long rejectedAttempts,
        long affectedSymbols,
        LocalDateTime lastAttemptAt,
        Map<String, Long> byTrigger,
        Map<String, Long> byOperation,
        Map<String, Long> byOutcome
) {
}

