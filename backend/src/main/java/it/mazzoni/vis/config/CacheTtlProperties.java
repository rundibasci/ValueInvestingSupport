package it.mazzoni.vis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.cache.ttl")
public record CacheTtlProperties(
        Duration quote,
        Duration ratios,
        Duration fundamentals,
        Duration profile
) {}
