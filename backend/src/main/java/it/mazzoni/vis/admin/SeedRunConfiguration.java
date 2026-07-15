package it.mazzoni.vis.admin;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@Profile("!demo")
@EnableConfigurationProperties(SeedRunProperties.class)
public class SeedRunConfiguration {
    @Bean("seedRunExecutor")
    Executor seedRunExecutor(SeedRunProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workerConcurrency());
        executor.setMaxPoolSize(properties.workerConcurrency());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("seed-run-");
        executor.initialize();
        return executor;
    }
}
