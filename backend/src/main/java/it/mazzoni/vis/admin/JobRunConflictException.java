package it.mazzoni.vis.admin;

import java.util.UUID;

class JobRunConflictException extends RuntimeException {
    private final String jobName;
    private final UUID activeRunId;

    JobRunConflictException(String jobName, UUID activeRunId) {
        super("Job is already running");
        this.jobName = jobName;
        this.activeRunId = activeRunId;
    }

    String jobName() {
        return jobName;
    }

    UUID activeRunId() {
        return activeRunId;
    }
}
