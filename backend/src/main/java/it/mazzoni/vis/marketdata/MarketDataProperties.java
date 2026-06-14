package it.mazzoni.vis.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-data")
public record MarketDataProperties(String source) {}
