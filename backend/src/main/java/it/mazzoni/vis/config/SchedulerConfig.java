package it.mazzoni.vis.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Registers Spring's {@code @Scheduled} infrastructure — except in K2 mode,
 * where {@code APP_JOBS_SCHEDULING_ENABLED=false} keeps this whole
 * configuration class from loading, so no {@code @Scheduled} trigger is ever
 * registered on the Cloud Run API service. Background work runs exclusively
 * through Cloud Run Jobs instead (see
 * {@code it.mazzoni.vis.jobs.CloudRunJobEntryPoint}); duplicating it here
 * would run every job twice. See {@link K2SchedulingGuard}.
 *
 * <p>{@code JobsProperties} itself is registered unconditionally on
 * {@code VisApplication} (not here), so it remains available to
 * {@link K2SchedulingGuard} and {@code JobRunLogger} even when this
 * configuration is skipped.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.jobs", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerConfig {

    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("ingestion-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        return scheduler;
    }
}
