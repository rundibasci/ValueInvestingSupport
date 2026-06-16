package it.mazzoni.vis.jobs;

import it.mazzoni.vis.domain.entity.JobRunLog;
import it.mazzoni.vis.domain.repository.JobRunLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JobRunLogger.class, JobLogWriter.class})
class JobRunLoggerTest {

    @Autowired
    private JobRunLogger jobRunLogger;

    @Autowired
    private JobRunLogRepository repository;

    @Test
    void successfulTask_persistsSuccessRecord() {
        int result = jobRunLogger.run("test-job", () -> 42);

        assertThat(result).isEqualTo(42);
        Optional<JobRunLog> log = repository.findTop1ByJobNameOrderByStartedAtDesc("test-job");
        assertThat(log).isPresent();
        assertThat(log.get().getStatus()).isEqualTo("SUCCESS");
        assertThat(log.get().getRecordsProcessed()).isEqualTo(42);
        assertThat(log.get().getCompletedAt()).isNotNull();
        assertThat(log.get().getErrorMessage()).isNull();
    }

    @Test
    void failingTask_persistsFailedRecordAndRethrows() {
        RuntimeException cause = new RuntimeException("boom");

        assertThatThrownBy(() -> jobRunLogger.run("failing-job", () -> { throw cause; }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        Optional<JobRunLog> log = repository.findTop1ByJobNameOrderByStartedAtDesc("failing-job");
        assertThat(log).isPresent();
        assertThat(log.get().getStatus()).isEqualTo("FAILED");
        assertThat(log.get().getErrorMessage()).isEqualTo("boom");
        assertThat(log.get().getRecordsProcessed()).isNull();
    }
}
