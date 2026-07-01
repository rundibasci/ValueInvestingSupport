package it.mazzoni.vis.admin;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobRunStatusResponse(
        UUID jobRunId,
        String jobName,
        String status,
        Integer recordsProcessed,
        Integer totalSymbols,
        long elapsedSeconds,
        long errorCount,
        String scopeSymbols,
        String scopeExchange,
        String scopeDataTypes,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorMessage
) {
}
