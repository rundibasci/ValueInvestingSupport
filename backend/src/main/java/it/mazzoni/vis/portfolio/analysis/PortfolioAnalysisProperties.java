package it.mazzoni.vis.portfolio.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.portfolio-analysis")
public record PortfolioAnalysisProperties(int workerConcurrency, int queueCapacity, int pollingIntervalMs) {
    public PortfolioAnalysisProperties {
        if (workerConcurrency < 1) workerConcurrency = 1;
        if (queueCapacity < 1) queueCapacity = 10;
        if (pollingIntervalMs < 500) pollingIntervalMs = 1500;
    }
}
