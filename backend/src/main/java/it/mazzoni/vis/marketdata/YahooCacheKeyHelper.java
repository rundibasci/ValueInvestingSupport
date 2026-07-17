package it.mazzoni.vis.marketdata;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component("yahooCacheKeyHelper")
public class YahooCacheKeyHelper {
    public String key(String symbol) {
        return "yf:" + CacheSchema.YAHOO_VERSION + ":" + symbol.toUpperCase(Locale.ROOT);
    }
}
