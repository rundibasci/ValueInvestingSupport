package it.mazzoni.vis.config;

import it.mazzoni.vis.client.yahoo.dto.*;
import it.mazzoni.vis.domain.*;
import it.mazzoni.vis.marketdata.CacheSchema;
import it.mazzoni.vis.marketdata.CacheKeyHelper;
import it.mazzoni.vis.marketdata.YahooCacheKeyHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.cache.type=redis",
        "market-data.source=fmp",
        "fmp.api-key=test"
})
@ActiveProfiles("demo")
class RedisCacheContractIT {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired CacheManager caches;
    @Autowired CacheKeyHelper marketKeys;
    @Autowired YahooCacheKeyHelper yahooKeys;

    @BeforeEach
    void clearCaches() {
        caches.getCacheNames().forEach(name -> required(name).clear());
    }

    @Test
    void productionCacheFamiliesRoundTripThroughRedis() {
        QuoteSummaryResponse summary = new QuoteSummaryResponse(new QuoteSummaryData(List.of(
                new QuoteSummaryResult(null, null, null, null, null, null, null)), null));
        ChartResponse chart = new ChartResponse(new ChartData(List.of(
                new ChartResult(new ChartMeta("AAPL", "USD", 200.0, "Apple", "Apple", "NMS"),
                        new ChartIndicators(List.of(new ChartQuote(List.of(1L)))))), null));
        CompanyProfile profile = new CompanyProfile("AAPL", "Apple", "Technology", "Hardware",
                "US", "USD", "NASDAQ", BigDecimal.TEN, "Profile", "https://example.invalid");
        FundamentalSnapshot fundamentals = new FundamentalSnapshot("AAPL", "Apple", "Technology",
                "Hardware", "US", "USD", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, 10L,
                List.of(BigDecimal.TEN), List.of(BigDecimal.ONE), List.of(BigDecimal.ONE),
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE);
        RatioSnapshot ratio = new RatioSnapshot("AAPL", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        MarketPriceQuote quote = new MarketPriceQuote("AAPL", BigDecimal.TEN, "USD",
                BigDecimal.ONE, BigDecimal.ONE, 100L);

        assertRoundTrip(CacheSchema.YAHOO_QUOTE_SUMMARY, yahooKeys.key("AAPL"), summary,
                QuoteSummaryResponse.class);
        assertRoundTrip(CacheSchema.YAHOO_CHART, yahooKeys.key("AAPL"), chart, ChartResponse.class);
        assertRoundTrip("mdc-profile", marketKeys.key("profile", "AAPL"), profile, CompanyProfile.class);
        assertRoundTrip("mdc-fundamentals", marketKeys.key("fundamentals", "AAPL"), fundamentals,
                FundamentalSnapshot.class);
        assertRoundTrip("mdc-ratios", marketKeys.key("ratios", "AAPL"), ratio, RatioSnapshot.class);
        assertRoundTrip("mdc-quote", marketKeys.key("quote", "AAPL"), quote, MarketPriceQuote.class);

        Cache annual = required("mdc-annual-ratios");
        annual.put(marketKeys.key("annual-ratios", "AAPL"), List.of(ratio));
        Object restored = annual.get(marketKeys.key("annual-ratios", "AAPL")).get();
        assertThat(restored).isInstanceOf(List.class);
        assertThat(restored).isEqualTo(List.of(ratio));
    }

    private <T> void assertRoundTrip(String cacheName, String key, T value, Class<T> type) {
        Cache cache = required(cacheName);
        cache.put(key, value);
        assertThat(cache.get(key, type)).isEqualTo(value);
    }

    private Cache required(String name) {
        return java.util.Objects.requireNonNull(caches.getCache(name), name);
    }
}
