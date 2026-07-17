package it.mazzoni.vis.marketdata;

import org.springframework.stereotype.Component;

@Component("cacheKeyHelper")
public class CacheKeyHelper {

    private static final String MARKET_DATA_SCHEMA_VERSION = "v10";

    private final String source;

    public CacheKeyHelper(MarketDataProperties properties) {
        this.source = properties.source() != null ? properties.source() : "unknown";
    }

    public String key(String endpoint, String symbol) {
        return "mdc:" + source + ":" + endpoint + ":" + MARKET_DATA_SCHEMA_VERSION + ":" + symbol.toUpperCase();
    }
}
