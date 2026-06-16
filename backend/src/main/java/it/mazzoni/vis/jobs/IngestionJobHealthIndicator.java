package it.mazzoni.vis.jobs;

import it.mazzoni.vis.domain.entity.JobRunLog;
import it.mazzoni.vis.domain.repository.JobRunLogRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component("ingestionJobs")
public class IngestionJobHealthIndicator implements HealthIndicator {

    private static final Map<String, Duration> EXPECTED_WINDOWS = Map.of(
            "bulk-profile-sync",      Duration.ofHours(26),
            "bulk-fundamentals-sync", Duration.ofHours(26),
            "bulk-ratios-sync",       Duration.ofHours(26),
            "bulk-dcf-sync",          Duration.ofHours(26),
            "quote-refresh",          Duration.ofMinutes(20),
            "dividend-update",        Duration.ofHours(26),
            "insider-trading",        Duration.ofMinutes(90)
    );

    private final JobRunLogRepository repository;

    public IngestionJobHealthIndicator(JobRunLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean allHealthy = true;

        for (Map.Entry<String, Duration> entry : EXPECTED_WINDOWS.entrySet()) {
            String jobName = entry.getKey();
            Duration window = entry.getValue();
            Optional<JobRunLog> latest = repository.findTop1ByJobNameOrderByStartedAtDesc(jobName);

            if (latest.isEmpty()) {
                details.put(jobName, "NEVER_RUN");
                allHealthy = false;
            } else {
                JobRunLog runLog = latest.get();
                String status = runLog.getStatus();
                LocalDateTime completedAt = runLog.getCompletedAt();
                String timestamp = completedAt != null ? completedAt.toString() : runLog.getStartedAt().toString();

                if ("FAILED".equals(status)) {
                    details.put(jobName, "FAILED at " + timestamp);
                    allHealthy = false;
                } else if ("RUNNING".equals(status)) {
                    details.put(jobName, "RUNNING since " + runLog.getStartedAt());
                } else {
                    boolean overdue = completedAt != null
                            && completedAt.isBefore(LocalDateTime.now().minus(window));
                    if (overdue) {
                        details.put(jobName, "OVERDUE (last SUCCESS: " + timestamp + ")");
                        allHealthy = false;
                    } else {
                        Integer count = runLog.getRecordsProcessed();
                        details.put(jobName, "SUCCESS " + timestamp
                                + (count != null ? " (" + count + " records)" : ""));
                    }
                }
            }
        }

        return allHealthy
                ? Health.up().withDetails(details).build()
                : Health.down().withDetails(details).build();
    }
}
