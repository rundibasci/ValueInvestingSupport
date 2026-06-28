package it.mazzoni.vis.observability;

import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class JobMetrics {

    private final ObservabilitySupport observability;

    public JobMetrics(ObservabilitySupport observability) {
        this.observability = observability;
    }

    public void recordStart(String jobName) {
        observability.count("vis.job.execution",
                observability.tags("job", jobName, "outcome", "started"));
    }

    public void recordSuccess(String jobName, long durationNanos) {
        observability.count("vis.job.execution",
                observability.tags("job", jobName, "outcome", "success"));
        observability.recordTime("vis.job.duration",
                observability.tags("job", jobName),
                "success",
                "none",
                durationNanos);
    }

    public void recordFailure(String jobName, String error, long durationNanos) {
        observability.count("vis.job.execution",
                observability.tags("job", jobName, "outcome", "error", "error", error));
        observability.recordTime("vis.job.duration",
                observability.tags("job", jobName),
                "error",
                error,
                durationNanos);
    }
}
