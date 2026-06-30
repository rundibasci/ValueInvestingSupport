package it.mazzoni.vis.admin;

import it.mazzoni.vis.config.JobsProperties;
import it.mazzoni.vis.domain.entity.IngestionEvent;
import it.mazzoni.vis.domain.entity.JobRunLog;
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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void listJobs_includesCronEnabledAndLatestRun() {
        JobRunLog older = runLog("quote-refresh", "SUCCESS", LocalDateTime.now().minusHours(2));
        JobRunLog newer = runLog("quote-refresh", "FAILED", LocalDateTime.now().minusHours(1));
        jobRunLogRepository.saveAll(List.of(older, newer));

        Map<String, JobDefinition> registry = new LinkedHashMap<>();
        registry.put("quote-refresh", new JobDefinition("quote-refresh", "quote-refresh", () -> {}));

        List<JobSummaryResponse> jobs = service.listJobs(registry);

        assertThat(jobs).hasSize(1);
        assertThat(jobs.getFirst().cronExpression()).isEqualTo("0 */15 * * * *");
        assertThat(jobs.getFirst().enabled()).isTrue();
        assertThat(jobs.getFirst().lastRun().status()).isEqualTo("FAILED");
        assertThat(jobs.getFirst().lastRun().id()).isEqualTo(newer.getId());
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
