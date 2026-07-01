package it.mazzoni.vis.admin;

import java.util.UUID;

public record JobTriggerResponse(
        String jobName,
        String status,
        UUID jobRunId
) {
}
