package it.mazzoni.vis.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * K2's replacement for {@link K1DeploymentGuard}'s single-instance
 * constraint: the K2 Cloud Run API service must never run in-process
 * {@code @Scheduled} work, because every background task runs exclusively
 * through Cloud Run Jobs + Cloud Scheduler instead (see
 * {@code it.mazzoni.vis.jobs.CloudRunJobEntryPoint}). Horizontal scaling is
 * therefore safe in K2 mode, but only once
 * {@code app.jobs.scheduling-enabled=false} keeps {@code SchedulerConfig}
 * from registering any {@code @Scheduled} trigger on the API service —
 * running both the in-process scheduler and Cloud Run Jobs at once would
 * duplicate every job execution.
 *
 * <p>{@code app.jobs.enabled} is a separate, unaffected switch: it gates
 * whether a job's body actually runs once invoked (checked by
 * {@code JobRunLogger}), regardless of whether the invocation came from
 * {@code @Scheduled} or from a Cloud Run Jobs {@code --job=} run. This
 * guard only cares about the trigger, not that runtime kill-switch.
 *
 * <p>This guard does not apply to a Cloud Run Jobs execution itself
 * ({@code --job=} process runs with {@code WebApplicationType.NONE} and
 * with {@code APP_DEPLOYMENT_MODE} left unset, so {@code deployment.isK2()}
 * is false there).
 */
@Component
public class K2SchedulingGuard implements ApplicationRunner {

    private final DeploymentProperties deployment;
    private final JobsProperties jobs;

    public K2SchedulingGuard(DeploymentProperties deployment, JobsProperties jobs) {
        this.deployment = deployment;
        this.jobs = jobs;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate(deployment, jobs);
    }

    static void validate(DeploymentProperties deployment, JobsProperties jobs) {
        if (deployment.isK2() && jobs.schedulingEnabled()) {
            throw new IllegalStateException(
                    "K2 deployment rejected: the Cloud Run API service must run with "
                            + "APP_JOBS_SCHEDULING_ENABLED=false once background work has moved to Cloud Run Jobs, "
                            + "or every job would run twice");
        }
    }
}
