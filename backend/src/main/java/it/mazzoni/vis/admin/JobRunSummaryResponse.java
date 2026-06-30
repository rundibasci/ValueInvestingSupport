package it.mazzoni.vis.admin;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobRunSummaryResponse(
        UUID id,
        String jobName,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String status,
        Integer recordsProcessed,
        String errorMessage
) {
}
