package it.mazzoni.vis.marketdata;

import org.springframework.stereotype.Component;

@Component("cacheKeyHelper")
public class CacheKeyHelper {

    private final String source;

    public CacheKeyHelper(MarketDataProperties properties) {
        this.source = properties.source() != null ? properties.source() : "unknown";
    }

    public String key(String endpoint, String symbol) {
        return "mdc:" + source + ":" + endpoint + ":" + symbol.toUpperCase();
    }
}
