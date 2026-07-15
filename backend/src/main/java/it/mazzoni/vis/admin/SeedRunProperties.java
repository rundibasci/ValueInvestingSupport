package it.mazzoni.vis.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seed-runs")
public record SeedRunProperties(int asyncThreshold, int maxSymbols, int workerConcurrency,
                                int queueCapacity, int retentionDays, int pollingIntervalMs) {
    public SeedRunProperties {
        if (asyncThreshold <= 0) asyncThreshold = 10;
        if (maxSymbols <= 0) maxSymbols = 500;
        if (workerConcurrency <= 0) workerConcurrency = 2;
        if (queueCapacity <= 0) queueCapacity = 20;
        if (retentionDays <= 0) retentionDays = 30;
        if (pollingIntervalMs <= 0) pollingIntervalMs = 1500;
    }
}
