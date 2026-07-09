package it.mazzoni.vis.realdemo;

import it.mazzoni.vis.admin.SeedResult;
import it.mazzoni.vis.admin.SeedService;
import it.mazzoni.vis.alerts.AlertDeliveryService;
import it.mazzoni.vis.alerts.AlertDetectionService;
import it.mazzoni.vis.domain.entity.JobRunLog;
import it.mazzoni.vis.domain.repository.JobRunLogRepository;
import it.mazzoni.vis.jobs.DividendUpdateJob;
import it.mazzoni.vis.jobs.IngestionEventRecorder;
import it.mazzoni.vis.jobs.QuoteRefreshJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("realDemo")
public class RealDemoStartupRunner {

    static final String JOB_NAME = "real-demo-startup";
    private static final Logger log = LoggerFactory.getLogger(RealDemoStartupRunner.class);

    private final RealDemoProperties properties;
    private final SeedService seedService;
    private final QuoteRefreshJob quoteRefreshJob;
    private final DividendUpdateJob dividendUpdateJob;
    private final AlertDetectionService alertDetectionService;
    private final AlertDeliveryService alertDeliveryService;
    private final IngestionEventRecorder eventRecorder;
    private final JobRunLogRepository jobRunLogRepository;

    public RealDemoStartupRunner(RealDemoProperties properties,
                                 SeedService seedService,
                                 QuoteRefreshJob quoteRefreshJob,
                                 DividendUpdateJob dividendUpdateJob,
                                 AlertDetectionService alertDetectionService,
                                 AlertDeliveryService alertDeliveryService,
                                 IngestionEventRecorder eventRecorder,
                                 JobRunLogRepository jobRunLogRepository) {
        this.properties = properties;
        this.seedService = seedService;
        this.quoteRefreshJob = quoteRefreshJob;
        this.dividendUpdateJob = dividendUpdateJob;
        this.alertDetectionService = alertDetectionService;
        this.alertDeliveryService = alertDeliveryService;
        this.eventRecorder = eventRecorder;
        this.jobRunLogRepository = jobRunLogRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(30)
    public void runStartupIngestion() {
        List<String> symbols = properties.tickers();
        JobRunLog runLog = startRun(symbols);
        MDC.put("job.name", JOB_NAME);
        MDC.put("job.run.id", runLog.getId().toString());
        try {
            log.info("real_demo_startup_started symbols={}", String.join(",", symbols));
            List<SeedResult> seedResults = seedService.seedTickers(symbols);
            int processed = recordSeedEvents(seedResults);
            processed += quoteRefreshJob.execute();
            processed += runOptionalDividendUpdate();
            int alertsCreated = alertDetectionService.execute();
            alertDeliveryService.deliverPendingHighPriorityAlerts();
            eventRecorder.record("ALL", "alert", "SUCCESS", "created alerts: " + alertsCreated);
            processed += alertsCreated;
            completeRun(runLog, "SUCCESS", processed, null);
            log.info("real_demo_startup_completed recordsProcessed={}", processed);
        } catch (RuntimeException e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            completeRun(runLog, "FAILED", null, message);
            log.error("real_demo_startup_failed message={}", message, e);
            throw e;
        } finally {
            MDC.remove("job.name");
            MDC.remove("job.run.id");
        }
    }

    private int runOptionalDividendUpdate() {
        try {
            return dividendUpdateJob.execute();
        } catch (UnsupportedOperationException e) {
            String message = e.getMessage() != null ? e.getMessage() : "dividend history unavailable";
            eventRecorder.record("ALL", "dividend", "SKIPPED", message);
            log.warn("real_demo_dividend_update_skipped message={}", message);
            return 0;
        }
    }

    private JobRunLog startRun(List<String> symbols) {
        JobRunLog runLog = new JobRunLog();
        runLog.setJobName(JOB_NAME);
        runLog.setStartedAt(LocalDateTime.now());
        runLog.setStatus("RUNNING");
        runLog.setScopeSymbols(String.join(",", symbols));
        runLog.setScopeDataTypes("profile,fundamentals,ratios,quote,valuation,score,dividend,alert");
        return jobRunLogRepository.save(runLog);
    }

    private int recordSeedEvents(List<SeedResult> seedResults) {
        int successes = 0;
        for (SeedResult result : seedResults) {
            if (result.error() == null) {
                eventRecorder.record(result.symbol(), "seed", "SUCCESS", null);
                successes++;
            } else {
                eventRecorder.record(result.symbol(), "seed", "FAILED", result.error());
            }
        }
        return successes;
    }

    private void completeRun(JobRunLog runLog, String status, Integer recordsProcessed, String errorMessage) {
        runLog.setCompletedAt(LocalDateTime.now());
        runLog.setStatus(status);
        runLog.setRecordsProcessed(recordsProcessed);
        runLog.setErrorMessage(errorMessage);
        jobRunLogRepository.save(runLog);
    }
}
