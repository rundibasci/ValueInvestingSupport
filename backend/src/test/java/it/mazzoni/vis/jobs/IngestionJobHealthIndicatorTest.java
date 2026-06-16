package it.mazzoni.vis.jobs;

import it.mazzoni.vis.domain.entity.JobRunLog;
import it.mazzoni.vis.domain.repository.JobRunLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IngestionJobHealthIndicator.class)
class IngestionJobHealthIndicatorTest {

    @Autowired
    private IngestionJobHealthIndicator indicator;

    @Autowired
    private JobRunLogRepository repository;

    private static final String[] ALL_JOBS = {
            "bulk-profile-sync", "bulk-fundamentals-sync", "bulk-ratios-sync", "bulk-dcf-sync",
            "quote-refresh", "dividend-update", "insider-trading"
    };

    @BeforeEach
    void clearLogs() {
        repository.deleteAll();
    }

    @Test
    void allJobsRecentSuccess_returnsUp() {
        LocalDateTime now = LocalDateTime.now();
        for (String jobName : ALL_JOBS) {
            repository.save(successLog(jobName, now.minusMinutes(5)));
        }

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKeys(ALL_JOBS);
    }

    @Test
    void oneJobFailed_returnsDown() {
        LocalDateTime now = LocalDateTime.now();
        for (String jobName : ALL_JOBS) {
            repository.save(successLog(jobName, now.minusMinutes(5)));
        }
        repository.save(failedLog("bulk-profile-sync", now.minusMinutes(2)));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("bulk-profile-sync").toString()).contains("FAILED");
    }

    @Test
    void oneJobNeverRun_returnsDown() {
        LocalDateTime now = LocalDateTime.now();
        for (String jobName : ALL_JOBS) {
            if (!jobName.equals("quote-refresh")) {
                repository.save(successLog(jobName, now.minusMinutes(5)));
            }
        }

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("quote-refresh").toString()).contains("NEVER_RUN");
    }

    private JobRunLog successLog(String jobName, LocalDateTime completedAt) {
        JobRunLog log = new JobRunLog();
        log.setJobName(jobName);
        log.setStartedAt(completedAt.minusSeconds(30));
        log.setCompletedAt(completedAt);
        log.setStatus("SUCCESS");
        log.setRecordsProcessed(10);
        return log;
    }

    private JobRunLog failedLog(String jobName, LocalDateTime completedAt) {
        JobRunLog log = new JobRunLog();
        log.setJobName(jobName);
        log.setStartedAt(completedAt.minusSeconds(5));
        log.setCompletedAt(completedAt);
        log.setStatus("FAILED");
        log.setErrorMessage("connection refused");
        return log;
    }
}
