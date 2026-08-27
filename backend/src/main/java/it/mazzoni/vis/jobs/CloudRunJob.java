package it.mazzoni.vis.jobs;

/**
 * Implemented by every background job that can run either as an in-process
 * {@code @Scheduled} method (local/K1) or as a standalone Cloud Run Jobs
 * execution (K2), invoked via {@code --job=<jobKey>} through
 * {@link CloudRunJobEntryPoint}. Both paths call the same {@code run()}
 * method, so job logic, idempotency, and {@code JobRunLog} observability are
 * identical regardless of how the run was triggered.
 */
public interface CloudRunJob {

    /**
     * Stable identifier used both as the {@code --job=} CLI argument value
     * and as the {@code JobRunLog} job name. Must be unique across all jobs.
     */
    String jobKey();

    /** Executes one run of this job, recording it via {@code JobRunLogger}. */
    void run();
}
