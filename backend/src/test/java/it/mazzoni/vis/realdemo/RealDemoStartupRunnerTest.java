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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class RealDemoStartupRunnerTest {

    @Test
    void runStartupIngestion_logsRunAndExecutesSeedRefreshDividendAndAlerts() {
        RealDemoProperties properties = new RealDemoProperties("KO,JNJ");
        SeedService seedService = mock(SeedService.class);
        QuoteRefreshJob quoteRefreshJob = mock(QuoteRefreshJob.class);
        DividendUpdateJob dividendUpdateJob = mock(DividendUpdateJob.class);
        AlertDetectionService alertDetectionService = mock(AlertDetectionService.class);
        AlertDeliveryService alertDeliveryService = mock(AlertDeliveryService.class);
        IngestionEventRecorder eventRecorder = mock(IngestionEventRecorder.class);
        JobRunLogRepository jobRunLogRepository = mock(JobRunLogRepository.class);
        when(jobRunLogRepository.save(any(JobRunLog.class))).thenAnswer(invocation -> {
            JobRunLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
            }
            return log;
        });
        when(seedService.seedTickers(List.of("KO", "JNJ"))).thenReturn(List.of(
                new SeedResult("KO", "Coca-Cola Co.", "Consumer Defensive", "NYSE", "US", "desc",
                        null, null, null, null, null, "yahoo", "seeded", null, LocalDate.now(), null),
                new SeedResult("JNJ", null, null, null, null, null,
                        null, null, null, null, null, null, "failed", null, null, "provider unavailable")
        ));
        when(quoteRefreshJob.execute()).thenReturn(1);
        when(dividendUpdateJob.execute()).thenReturn(2);
        when(alertDetectionService.execute()).thenReturn(3);

        RealDemoStartupRunner runner = new RealDemoStartupRunner(properties, seedService, quoteRefreshJob,
                dividendUpdateJob, alertDetectionService, alertDeliveryService, eventRecorder, jobRunLogRepository);

        runner.runStartupIngestion();

        verify(seedService).seedTickers(List.of("KO", "JNJ"));
        verify(quoteRefreshJob).execute();
        verify(dividendUpdateJob).execute();
        verify(alertDetectionService).execute();
        verify(alertDeliveryService).deliverPendingHighPriorityAlerts();
        verify(eventRecorder).record("KO", "seed", "SUCCESS", null);
        verify(eventRecorder).record("JNJ", "seed", "FAILED", "provider unavailable");
        verify(eventRecorder).record("ALL", "alert", "SUCCESS", "created alerts: 3");
        ArgumentCaptor<JobRunLog> runCaptor = ArgumentCaptor.forClass(JobRunLog.class);
        verify(jobRunLogRepository, org.mockito.Mockito.atLeast(2)).save(runCaptor.capture());
        assertThat(runCaptor.getAllValues().getLast().getStatus()).isEqualTo("SUCCESS");
        assertThat(runCaptor.getAllValues().getLast().getRecordsProcessed()).isEqualTo(7);
    }

    @Test
    void runStartupIngestion_skipsUnsupportedDividendHistoryWithoutFailingDemoStartup() {
        RealDemoProperties properties = new RealDemoProperties("KO");
        SeedService seedService = mock(SeedService.class);
        QuoteRefreshJob quoteRefreshJob = mock(QuoteRefreshJob.class);
        DividendUpdateJob dividendUpdateJob = mock(DividendUpdateJob.class);
        AlertDetectionService alertDetectionService = mock(AlertDetectionService.class);
        AlertDeliveryService alertDeliveryService = mock(AlertDeliveryService.class);
        IngestionEventRecorder eventRecorder = mock(IngestionEventRecorder.class);
        JobRunLogRepository jobRunLogRepository = mock(JobRunLogRepository.class);
        when(jobRunLogRepository.save(any(JobRunLog.class))).thenAnswer(invocation -> {
            JobRunLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
            }
            return log;
        });
        when(seedService.seedTickers(List.of("KO"))).thenReturn(List.of(
                new SeedResult("KO", "Coca-Cola Co.", "Consumer Defensive", "NYSE", "US", "desc",
                        null, null, null, null, null, "yahoo", "seeded", null, LocalDate.now(), null)
        ));
        when(quoteRefreshJob.execute()).thenReturn(1);
        doThrow(new UnsupportedOperationException("getDividendHistory is not supported by the Yahoo Finance client"))
                .when(dividendUpdateJob).execute();
        when(alertDetectionService.execute()).thenReturn(1);

        RealDemoStartupRunner runner = new RealDemoStartupRunner(properties, seedService, quoteRefreshJob,
                dividendUpdateJob, alertDetectionService, alertDeliveryService, eventRecorder, jobRunLogRepository);

        runner.runStartupIngestion();

        verify(eventRecorder).record("ALL", "dividend", "SKIPPED",
                "getDividendHistory is not supported by the Yahoo Finance client");
        ArgumentCaptor<JobRunLog> runCaptor = ArgumentCaptor.forClass(JobRunLog.class);
        verify(jobRunLogRepository, org.mockito.Mockito.atLeast(2)).save(runCaptor.capture());
        assertThat(runCaptor.getAllValues().getLast().getStatus()).isEqualTo("SUCCESS");
        assertThat(runCaptor.getAllValues().getLast().getRecordsProcessed()).isEqualTo(3);
    }
}
