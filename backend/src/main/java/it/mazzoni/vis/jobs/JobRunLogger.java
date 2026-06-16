package it.mazzoni.vis.jobs;

import it.mazzoni.vis.domain.entity.JobRunLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class JobRunLogger {

    private static final Logger log = LoggerFactory.getLogger(JobRunLogger.class);

    private final JobLogWriter writer;

    public JobRunLogger(JobLogWriter writer) {
        this.writer = writer;
    }

    /**
     * Runs {@code task}, logging start/success/failure to {@code job_run_log}.
     * Each log write is in its own REQUIRES_NEW transaction so it commits even
     * if the task body throws and its transaction rolls back.
     *
     * @return number of records processed, as returned by {@code task}
     */
    public int run(String jobName, Supplier<Integer> task) {
        JobRunLog runLog = writer.start(jobName);
        log.info("Job [{}] started", jobName);
        try {
            int count = task.get();
            writer.succeed(runLog.getId(), count);
            log.info("Job [{}] completed — {} records", jobName, count);
            return count;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            writer.fail(runLog.getId(), msg);
            log.error("Job [{}] failed: {}", jobName, msg, e);
            throw e;
        }
    }
}
