package it.mazzoni.vis.admin;

import java.time.LocalDateTime;
import java.util.UUID;

public record IngestionEventResponse(
        UUID id,
        UUID jobRunId,
        String jobName,
        String symbol,
        String dataType,
        String status,
        String source,
        String errorDetail,
        LocalDateTime occurredAt
) {
}
