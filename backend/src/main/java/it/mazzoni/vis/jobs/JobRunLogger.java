package it.mazzoni.vis.jobs;

import it.mazzoni.vis.domain.entity.JobRunLog;
import it.mazzoni.vis.observability.JobMetrics;
import it.mazzoni.vis.observability.ObservabilitySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class JobRunLogger {

    private static final Logger log = LoggerFactory.getLogger(JobRunLogger.class);

    private final JobLogWriter writer;
    private final JobMetrics metrics;

    public JobRunLogger(JobLogWriter writer, JobMetrics metrics) {
        this.writer = writer;
        this.metrics = metrics;
    }

    /**
     * Runs {@code task}, logging start/success/failure to {@code job_run_log}.
     * Each log write is in its own REQUIRES_NEW transaction so it commits even
     * if the task body throws and its transaction rolls back.
     *
     * @return number of records processed, as returned by {@code task}
     */
    public int run(String jobName, Supplier<Integer> task) {
        long start = System.nanoTime();
        JobRunLog runLog = writer.start(jobName);
        MDC.put("job.name", jobName);
        MDC.put("job.run.id", runLog.getId().toString());
        metrics.recordStart(jobName);
        log.info("job_started job={}", jobName);
        try {
            int count = task.get();
            writer.succeed(runLog.getId(), count);
            metrics.recordSuccess(jobName, System.nanoTime() - start);
            log.info("job_completed job={} recordsProcessed={}", jobName, count);
            return count;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            writer.fail(runLog.getId(), msg);
            metrics.recordFailure(jobName, ObservabilitySupport.safeError(e), System.nanoTime() - start);
            log.error("job_failed job={} error={} message={}", jobName, ObservabilitySupport.safeError(e), msg, e);
            throw e;
        } finally {
            MDC.remove("job.name");
            MDC.remove("job.run.id");
        }
    }
}
