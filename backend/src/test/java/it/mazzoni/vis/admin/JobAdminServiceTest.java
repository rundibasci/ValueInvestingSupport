package it.mazzoni.vis.admin;

import it.mazzoni.vis.config.JobsProperties;
import it.mazzoni.vis.domain.entity.IngestionEvent;
import it.mazzoni.vis.domain.entity.JobRunLog;
import it.mazzoni.vis.domain.repository.JobRuntimeSettingRepository;
import it.mazzoni.vis.domain.repository.IngestionEventRepository;
import it.mazzoni.vis.domain.repository.JobRunLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JobAdminService.class, JobAdminServiceTest.Config.class})
class JobAdminServiceTest {

    @Autowired
    private JobAdminService service;

    @Autowired
    private JobRunLogRepository jobRunLogRepository;

    @Autowired
    private IngestionEventRepository ingestionEventRepository;

    @Autowired
    private JobRuntimeSettingRepository jobRuntimeSettingRepository;

    @Test
    void listJobs_includesCronEnabledAndLatestRun() {
        JobRunLog older = runLog("list-job", "SUCCESS", LocalDateTime.now().minusHours(2));
        JobRunLog newer = runLog("list-job", "FAILED", LocalDateTime.now().minusHours(1));
        jobRunLogRepository.saveAll(List.of(older, newer));

        Map<String, JobDefinition> registry = new LinkedHashMap<>();
        registry.put("list-job", new JobDefinition("list-job", "quote-refresh", () -> 0));

        List<JobSummaryResponse> jobs = service.listJobs(registry);

        assertThat(jobs).hasSize(1);
        assertThat(jobs.getFirst().cronExpression()).isEqualTo("0 */15 * * * *");
        assertThat(jobs.getFirst().enabled()).isTrue();
        assertThat(jobs.getFirst().lastRun().status()).isEqualTo("FAILED");
        assertThat(jobs.getFirst().lastRun().id()).isEqualTo(newer.getId());
    }

    @Test
    void monitorJobs_includesOperationalState() {
        JobRunLog running = runLog("monitor-job", "RUNNING", LocalDateTime.now().minusMinutes(3));
        running.setCompletedAt(null);
        JobRunLog success = runLog("monitor-job", "SUCCESS", LocalDateTime.now().minusHours(2));
        JobRunLog failure = runLog("monitor-job", "FAILED", LocalDateTime.now().minusHours(1));
        failure.setErrorMessage("provider unavailable");
        jobRunLogRepository.saveAll(List.of(success, failure, running));
        ingestionEventRepository.save(event(running.getId(), "monitor-job", "AAPL", "quote", "SUCCESS"));

        Map<String, JobDefinition> registry = Map.of("monitor-job", new JobDefinition("monitor-job", "quote-refresh", () -> 0));

        JobMonitorResponse response = service.monitorJobs(registry).getFirst();

        assertThat(response.jobName()).isEqualTo("monitor-job");
        assertThat(response.enabled()).isTrue();
        assertThat(response.nextRunAt()).isNotNull();
        assertThat(response.currentStatus()).isEqualTo("RUNNING");
        assertThat(response.runningRun().id()).isEqualTo(running.getId());
        assertThat(response.lastSuccessfulRun().id()).isEqualTo(success.getId());
        assertThat(response.lastFailedRun().id()).isEqualTo(failure.getId());
        assertThat(response.latestError()).isEqualTo("provider unavailable");
        assertThat(response.dataSource()).isEqualTo("yahoo");
    }

    @Test
    void updateEnabled_persistsRuntimeStateAndListUsesIt() {
        JobDefinition definition = new JobDefinition("quote-refresh", "quote-refresh", () -> 0);

        JobSummaryResponse updated = service.updateEnabled(definition, false);

        assertThat(updated.enabled()).isFalse();
        assertThat(jobRuntimeSettingRepository.findById("quote-refresh")).hasValueSatisfying(setting ->
                assertThat(setting.isEnabled()).isFalse());
        assertThat(service.listJobs(Map.of("quote-refresh", definition)).getFirst().enabled()).isFalse();
        assertThat(service.monitorJobs(Map.of("quote-refresh", definition)).getFirst().nextRunAt()).isNull();
        assertThat(service.monitorJobs(Map.of("quote-refresh", definition)).getFirst().currentStatus()).isEqualTo("DISABLED");
        assertThat(service.monitorJobs(Map.of("quote-refresh", definition)).getFirst().latestError()).isNull();
    }

    @Test
    void updateCron_validatesAndPersistsOverride() {
        JobDefinition definition = new JobDefinition("quote-refresh", "quote-refresh", () -> 0);

        JobSummaryResponse updated = service.updateCron(definition, "0 0 4 * * *");

        assertThat(updated.cronExpression()).isEqualTo("0 0 4 * * *");
        assertThatThrownBy(() -> service.updateCron(definition, "not-a-cron"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void trigger_returnsPollableRunIdAndPersistsScope() throws Exception {
        AtomicBoolean ran = new AtomicBoolean(false);
        JobDefinition definition = new JobDefinition("quote-refresh", "quote-refresh", () -> {
            ran.set(true);
            return 2;
        });

        JobTriggerResponse response = service.trigger(definition, new JobRunRequest("aapl, msft", "nasdaq", "quote"));

        assertThat(response.status()).isEqualTo("triggered");
        awaitCompleted(response.jobRunId());
        JobRunStatusResponse status = service.runStatus(response.jobRunId()).orElseThrow();
        assertThat(ran).isTrue();
        assertThat(status.status()).isEqualTo("SUCCESS");
        assertThat(status.recordsProcessed()).isEqualTo(2);
        assertThat(status.totalSymbols()).isEqualTo(2);
        assertThat(status.scopeSymbols()).isEqualTo("AAPL,MSFT");
        assertThat(status.scopeExchange()).isEqualTo("NASDAQ");
        assertThat(status.scopeDataTypes()).isEqualTo("QUOTE");
    }

    @Test
    void trigger_disabledJobCreatesSkippedRun() {
        JobDefinition definition = new JobDefinition("quote-refresh", "quote-refresh", () -> 99);
        service.updateEnabled(definition, false);

        JobTriggerResponse response = service.trigger(definition, null);

        JobRunStatusResponse status = service.runStatus(response.jobRunId()).orElseThrow();
        assertThat(response.status()).isEqualTo("skipped");
        assertThat(status.status()).isEqualTo("SKIPPED");
        assertThat(status.recordsProcessed()).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void trigger_blocksDuplicateManualRunWhileRunning() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        JobDefinition definition = new JobDefinition("quote-refresh", "quote-refresh", () -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return 1;
        });

        JobTriggerResponse first = service.trigger(definition, null);
        awaitRunning(first.jobRunId());

        assertThatThrownBy(() -> service.trigger(definition, null))
                .isInstanceOf(JobRunConflictException.class)
                .satisfies(error -> assertThat(((JobRunConflictException) error).activeRunId()).isEqualTo(first.jobRunId()));

        release.countDown();
        awaitCompleted(first.jobRunId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void trigger_failedRunStoresRootCauseMessage() throws Exception {
        JobDefinition definition = new JobDefinition("quote-refresh", "quote-refresh", () -> {
            throw new RuntimeException(new RuntimeException("ReadTimeoutException"));
        });

        JobTriggerResponse response = service.trigger(definition, null);

        awaitCompleted(response.jobRunId());
        JobRunStatusResponse status = service.runStatus(response.jobRunId()).orElseThrow();
        assertThat(status.status()).isEqualTo("FAILED");
        assertThat(status.errorMessage()).isEqualTo("ReadTimeoutException");
    }

    @Test
    void events_filtersByRunSymbolAndStatus() {
        UUID targetRunId = UUID.randomUUID();
        ingestionEventRepository.save(event(targetRunId, "quote-refresh", "AAPL", "quote", "SUCCESS"));
        ingestionEventRepository.save(event(targetRunId, "quote-refresh", "MSFT", "quote", "FAILED"));
        ingestionEventRepository.save(event(UUID.randomUUID(), "quote-refresh", "AAPL", "quote", "FAILED"));
        ingestionEventRepository.save(event(targetRunId, "bulk-ratios-sync", "AAPL", "ratios", "SUCCESS"));

        PageResponse<IngestionEventResponse> page = service.events(
                "quote-refresh", targetRunId, "aapl", "success", 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().symbol()).isEqualTo("AAPL");
        assertThat(page.content().getFirst().status()).isEqualTo("SUCCESS");
    }

    private JobRunLog runLog(String jobName, String status, LocalDateTime startedAt) {
        JobRunLog log = new JobRunLog();
        log.setJobName(jobName);
        log.setStartedAt(startedAt);
        log.setCompletedAt(startedAt.plusMinutes(1));
        log.setStatus(status);
        log.setRecordsProcessed(3);
        return log;
    }

    private IngestionEvent event(UUID runId, String jobName, String symbol, String dataType, String status) {
        IngestionEvent event = new IngestionEvent();
        event.setJobRunId(runId);
        event.setJobName(jobName);
        event.setSymbol(symbol);
        event.setDataType(dataType);
        event.setStatus(status);
        event.setSource("yahoo");
        event.setOccurredAt(LocalDateTime.now());
        return event;
    }

    private void awaitCompleted(UUID runId) throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            JobRunStatusResponse status = service.runStatus(runId).orElseThrow();
            if (!"RUNNING".equals(status.status())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for job run " + runId);
    }

    private void awaitRunning(UUID runId) throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            JobRunStatusResponse status = service.runStatus(runId).orElseThrow();
            if ("RUNNING".equals(status.status())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for running job " + runId);
    }

    @TestConfiguration
    static class Config {
        @Bean
        JobsProperties jobsProperties() {
            return new JobsProperties(
                    true,
                    List.of("NYSE"),
                    Map.of("quote-refresh", "0 */15 * * * *")
            );
        }
    }
}
