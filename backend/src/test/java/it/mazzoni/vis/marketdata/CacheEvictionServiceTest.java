package it.mazzoni.vis.marketdata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"market-data.source=fmp", "fmp.api-key=test", "spring.cache.type=simple"})
@ActiveProfiles("demo")
class CacheEvictionServiceTest {

    @Autowired CacheEvictionService cacheEvictionService;
    @Autowired CacheManager cacheManager;
    @Autowired CacheKeyHelper cacheKeyHelper;
    @Autowired YahooCacheKeyHelper yahooCacheKeyHelper;

    @BeforeEach
    void clearAll() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void evictSymbol_removesEntriesFromAllCacheLayers() {
        String symbol = "AAPL";
        cacheManager.getCache("mdc-quote")       .put(cacheKeyHelper.key("quote",        symbol), "dummy");
        cacheManager.getCache("mdc-profile")     .put(cacheKeyHelper.key("profile",      symbol), "dummy");
        cacheManager.getCache("mdc-fundamentals").put(cacheKeyHelper.key("fundamentals", symbol), "dummy");
        cacheManager.getCache("mdc-ratios")      .put(cacheKeyHelper.key("ratios",       symbol), "dummy");
        cacheManager.getCache("mdc-annual-ratios").put(cacheKeyHelper.key("annual-ratios", symbol), "dummy");
        cacheManager.getCache(CacheSchema.YAHOO_QUOTE_SUMMARY).put(yahooCacheKeyHelper.key(symbol), "dummy");
        cacheManager.getCache(CacheSchema.YAHOO_CHART).put(yahooCacheKeyHelper.key(symbol), "dummy");

        cacheEvictionService.evictSymbol(symbol);

        assertThat(cacheManager.getCache("mdc-quote")       .get(cacheKeyHelper.key("quote",        symbol))).isNull();
        assertThat(cacheManager.getCache("mdc-profile")     .get(cacheKeyHelper.key("profile",      symbol))).isNull();
        assertThat(cacheManager.getCache("mdc-fundamentals").get(cacheKeyHelper.key("fundamentals", symbol))).isNull();
        assertThat(cacheManager.getCache("mdc-ratios")      .get(cacheKeyHelper.key("ratios",       symbol))).isNull();
        assertThat(cacheManager.getCache("mdc-annual-ratios").get(cacheKeyHelper.key("annual-ratios", symbol))).isNull();
        assertThat(cacheManager.getCache(CacheSchema.YAHOO_QUOTE_SUMMARY).get(yahooCacheKeyHelper.key(symbol))).isNull();
        assertThat(cacheManager.getCache(CacheSchema.YAHOO_CHART).get(yahooCacheKeyHelper.key(symbol))).isNull();
    }

    @Test
    void evictSymbol_doesNotAffectOtherSymbols() {
        cacheManager.getCache("mdc-quote").put(cacheKeyHelper.key("quote", "AAPL"), "dummy-aapl");
        cacheManager.getCache("mdc-quote").put(cacheKeyHelper.key("quote", "MSFT"), "dummy-msft");

        cacheEvictionService.evictSymbol("AAPL");

        assertThat(cacheManager.getCache("mdc-quote").get(cacheKeyHelper.key("quote", "AAPL"))).isNull();
        assertThat(cacheManager.getCache("mdc-quote").get(cacheKeyHelper.key("quote", "MSFT"))).isNotNull();
    }
}
