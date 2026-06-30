package it.mazzoni.vis.jobs;

import it.mazzoni.vis.domain.entity.IngestionEvent;
import it.mazzoni.vis.domain.repository.IngestionEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IngestionEventRecorder.class)
class IngestionEventRecorderTest {

    @Autowired
    private IngestionEventRecorder recorder;

    @Autowired
    private IngestionEventRepository repository;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void success_attachesCurrentJobRunContext() {
        UUID runId = UUID.randomUUID();
        MDC.put("job.name", "quote-refresh");
        MDC.put("job.run.id", runId.toString());

        recorder.success("aapl", "quote");

        List<IngestionEvent> events = repository.findAll();
        assertThat(events).hasSize(1);
        IngestionEvent event = events.getFirst();
        assertThat(event.getJobRunId()).isEqualTo(runId);
        assertThat(event.getJobName()).isEqualTo("quote-refresh");
        assertThat(event.getSymbol()).isEqualTo("AAPL");
        assertThat(event.getDataType()).isEqualTo("quote");
        assertThat(event.getStatus()).isEqualTo("SUCCESS");
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    void failed_recordsErrorDetail() {
        MDC.put("job.name", "bulk-ratios-sync");
        MDC.put("job.run.id", UUID.randomUUID().toString());

        recorder.failed("MSFT", "ratios", new RuntimeException("provider unavailable"));

        IngestionEvent event = repository.findAll().getFirst();
        assertThat(event.getStatus()).isEqualTo("FAILED");
        assertThat(event.getErrorDetail()).isEqualTo("provider unavailable");
    }
}
