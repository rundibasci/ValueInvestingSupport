package it.mazzoni.vis.marketdata;

import org.springframework.stereotype.Component;

@Component("cacheKeyHelper")
public class CacheKeyHelper {

    private static final String RATIOS_SCHEMA_VERSION = "v2";

    private final String source;

    public CacheKeyHelper(MarketDataProperties properties) {
        this.source = properties.source() != null ? properties.source() : "unknown";
    }

    public String key(String endpoint, String symbol) {
        String version = endpoint.contains("ratios") ? ":" + RATIOS_SCHEMA_VERSION : "";
        return "mdc:" + source + ":" + endpoint + version + ":" + symbol.toUpperCase();
    }
}
