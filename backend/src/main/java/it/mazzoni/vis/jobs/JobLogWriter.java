package it.mazzoni.vis.jobs;

import it.mazzoni.vis.domain.entity.JobRunLog;
import it.mazzoni.vis.domain.repository.JobRunLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
class JobLogWriter {

    private final JobRunLogRepository repository;

    JobLogWriter(JobRunLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    JobRunLog start(String jobName) {
        JobRunLog log = new JobRunLog();
        log.setJobName(jobName);
        log.setStartedAt(LocalDateTime.now());
        log.setStatus("RUNNING");
        return repository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void succeed(UUID logId, int recordsProcessed) {
        repository.findById(logId).ifPresent(log -> {
            log.setCompletedAt(LocalDateTime.now());
            log.setStatus("SUCCESS");
            log.setRecordsProcessed(recordsProcessed);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void fail(UUID logId, String errorMessage) {
        repository.findById(logId).ifPresent(log -> {
            log.setCompletedAt(LocalDateTime.now());
            log.setStatus("FAILED");
            log.setErrorMessage(errorMessage);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    JobRunLog skipped(String jobName, String reason) {
        JobRunLog log = new JobRunLog();
        log.setJobName(jobName);
        log.setStartedAt(LocalDateTime.now());
        log.setCompletedAt(LocalDateTime.now());
        log.setStatus("SKIPPED");
        log.setRecordsProcessed(0);
        log.setErrorMessage(reason);
        return repository.save(log);
    }
}
