package it.mazzoni.vis.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * K2 Cloud Run Jobs entry point. When the process is started with
 * {@code --job=<jobKey>}, runs exactly that {@link CloudRunJob} and returns
 * (see {@link it.mazzoni.vis.VisApplication#main} for the matching
 * non-web bootstrap and exit-code handling). With no {@code --job} argument
 * this runner is a no-op, so it is safe to leave active on the normal
 * web-serving Cloud Run API service as well.
 *
 * <p>One Cloud Run Job resource exists per {@link CloudRunJob#jobKey()},
 * each triggered on its existing cadence by its own Cloud Scheduler entry
 * (see {@code terraform/modules/cloud-scheduler}). The job logic itself,
 * including {@code JobRunLogger} bookkeeping and idempotency, is unchanged
 * from the in-process {@code @Scheduled} path — only how the run is
 * triggered differs.
 */
@Component
public class CloudRunJobEntryPoint implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CloudRunJobEntryPoint.class);

    private final Map<String, CloudRunJob> jobsByKey;

    public CloudRunJobEntryPoint(List<CloudRunJob> jobs) {
        this.jobsByKey = jobs.stream().collect(Collectors.toMap(CloudRunJob::jobKey, Function.identity()));
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("job")) {
            return;
        }
        List<String> requested = args.getOptionValues("job");
        String jobKey = requested == null || requested.isEmpty() ? "" : requested.get(0);
        CloudRunJob job = jobsByKey.get(jobKey);
        if (job == null) {
            throw new IllegalArgumentException(
                    "Unknown --job=%s. Known jobs: %s".formatted(jobKey, jobsByKey.keySet()));
        }
        log.info("cloud_run_job_invoked job={}", jobKey);
        job.run();
    }
}
