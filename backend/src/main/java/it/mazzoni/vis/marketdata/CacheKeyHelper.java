package it.mazzoni.vis.marketdata;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component("cacheKeyHelper")
public class CacheKeyHelper {

    private final String source;

    public CacheKeyHelper(MarketDataProperties properties) {
        this.source = properties.source() != null ? properties.source() : "unknown";
    }

    public String key(String endpoint, String symbol) {
        return "mdc:" + source + ":" + endpoint + ":" + CacheSchema.MARKET_DATA_VERSION + ":"
                + symbol.toUpperCase(Locale.ROOT);
    }
}
