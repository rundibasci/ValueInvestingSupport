package it.mazzoni.vis.admin;

public record JobSummaryResponse(
        String jobName,
        String cronExpression,
        boolean enabled,
        JobRunSummaryResponse lastRun
) {
}
