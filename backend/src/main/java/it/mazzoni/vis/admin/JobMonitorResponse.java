package it.mazzoni.vis.admin;

import java.time.LocalDateTime;

public record JobMonitorResponse(
        String jobName,
        String cronExpression,
        boolean enabled,
        LocalDateTime nextRunAt,
        String scheduleError,
        String currentStatus,
        long currentDurationSeconds,
        String dataSource,
        JobRunSummaryResponse runningRun,
        JobRunSummaryResponse lastRun,
        JobRunSummaryResponse lastSuccessfulRun,
        JobRunSummaryResponse lastFailedRun,
        String latestError
) {
}
