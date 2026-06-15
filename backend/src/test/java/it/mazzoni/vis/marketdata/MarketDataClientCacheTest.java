package it.mazzoni.vis.marketdata;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import it.mazzoni.vis.marketdata.fmp.FmpMarketDataClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"market-data.source=fmp", "fmp.api-key=test-key", "spring.cache.type=simple"})
@ActiveProfiles("demo")
class MarketDataClientCacheTest {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @AfterEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("fmp.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @Autowired FmpMarketDataClient client;
    @Autowired CacheManager cacheManager;
    @Autowired CacheKeyHelper cacheKeyHelper;

    @BeforeEach
    void clearCache() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    private static final String QUOTE_MSFT = """
            [{"symbol":"MSFT","name":"Microsoft Corporation","price":400.0,"change":1.5,"changesPercentage":0.38}]
            """;

    private static final String PROFILE_MSFT = """
            [{"symbol":"MSFT","companyName":"Microsoft Corporation","sector":"Technology",
              "industry":"Software","country":"US","currency":"USD",
              "exchangeShortName":"NASDAQ","mktCap":3000000000000,"price":400.0}]
            """;

    @Test
    void getQuote_secondCallHitsCache_wireMockReceivesOneRequest() {
        wireMock.stubFor(get(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo("MSFT"))
                .willReturn(okJson(QUOTE_MSFT)));

        var first  = client.getQuote("MSFT");
        var second = client.getQuote("MSFT");

        assertThat(second.price()).isEqualByComparingTo(first.price());
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo("MSFT")));
    }

    @Test
    void getProfile_secondCallHitsCache_wireMockReceivesOneRequest() {
        wireMock.stubFor(get(urlPathEqualTo("/profile"))
                .withQueryParam("symbol", equalTo("MSFT"))
                .willReturn(okJson(PROFILE_MSFT)));

        var first  = client.getProfile("MSFT");
        var second = client.getProfile("MSFT");

        assertThat(second.companyName()).isEqualTo(first.companyName());
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/profile"))
                .withQueryParam("symbol", equalTo("MSFT")));
    }

    @Test
    void getQuote_cacheKeyIsSymbolUppercased() {
        // The client forwards the symbol as-is to FMP; uppercase normalisation happens only in the cache key
        wireMock.stubFor(get(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo("msft"))
                .willReturn(okJson(QUOTE_MSFT)));

        client.getQuote("msft");

        String expectedKey = cacheKeyHelper.key("quote", "MSFT");
        assertThat(cacheManager.getCache("mdc-quote").get(expectedKey)).isNotNull();
    }
}
