package it.mazzoni.vis.portfolio.analysis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(PortfolioAnalysisProperties.class)
public class PortfolioAnalysisConfiguration {
    @Bean("portfolioAnalysisExecutor")
    Executor portfolioAnalysisExecutor(PortfolioAnalysisProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workerConcurrency());
        executor.setMaxPoolSize(properties.workerConcurrency());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("portfolio-analysis-");
        executor.initialize();
        return executor;
    }
}
