package it.mazzoni.vis.jobs;

import it.mazzoni.vis.config.JobsProperties;
import it.mazzoni.vis.domain.entity.JobRunLog;
import it.mazzoni.vis.domain.entity.JobRuntimeSetting;
import it.mazzoni.vis.domain.repository.JobRunLogRepository;
import it.mazzoni.vis.domain.repository.JobRuntimeSettingRepository;
import it.mazzoni.vis.observability.JobMetrics;
import it.mazzoni.vis.observability.ObservabilitySupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JobRunLogger.class, JobLogWriter.class, JobRunLoggerTest.MetricsTestConfig.class})
class JobRunLoggerTest {

    @Autowired
    private JobRunLogger jobRunLogger;

    @Autowired
    private JobRunLogRepository repository;

    @Autowired
    private JobRuntimeSettingRepository jobRuntimeSettingRepository;

    @Autowired
    private SimpleMeterRegistry meterRegistry;

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
        assertThat(meterRegistry.find("vis.job.execution").tag("job", "test-job").tag("outcome", "success").counter())
                .isNotNull();
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
        assertThat(meterRegistry.find("vis.job.execution").tag("job", "failing-job").tag("outcome", "error").counter())
                .isNotNull();
    }

    @Test
    void disabledJob_persistsSkippedRecordAndDoesNotRunTask() {
        JobRuntimeSetting setting = new JobRuntimeSetting();
        setting.setJobName("disabled-job");
        setting.setEnabled(false);
        jobRuntimeSettingRepository.save(setting);

        int result = jobRunLogger.run("disabled-job", () -> {
            throw new AssertionError("disabled task should not run");
        });

        assertThat(result).isZero();
        Optional<JobRunLog> log = repository.findTop1ByJobNameOrderByStartedAtDesc("disabled-job");
        assertThat(log).isPresent();
        assertThat(log.get().getStatus()).isEqualTo("SKIPPED");
        assertThat(log.get().getRecordsProcessed()).isZero();
    }

    @TestConfiguration
    static class MetricsTestConfig {
        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        ObservabilitySupport observabilitySupport(SimpleMeterRegistry meterRegistry) {
            return new ObservabilitySupport(meterRegistry);
        }

        @Bean
        JobMetrics jobMetrics(ObservabilitySupport observabilitySupport) {
            return new JobMetrics(observabilitySupport);
        }

        @Bean
        JobsProperties jobsProperties() {
            return new JobsProperties(true, List.of("NYSE"), Map.of("test-job", "0 0 2 * * *"));
        }
    }
}
