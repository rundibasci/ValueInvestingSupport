package it.mazzoni.vis.marketdata.fmp;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.marketdata.MarketDataException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"market-data.source=fmp", "fmp.api-key=test-key", "spring.cache.type=none"})
@ActiveProfiles("demo")
class FmpMarketDataClientTest {

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

    @Autowired
    FmpMarketDataClient client;

    // -----------------------------------------------------------------------
    // Fixture JSON snippets
    // -----------------------------------------------------------------------

    private static final String PROFILE_AAPL = """
            [{"symbol":"AAPL","companyName":"Apple Inc.","sector":"Technology",
              "industry":"Consumer Electronics","country":"US","currency":"USD",
              "exchangeShortName":"NASDAQ","mktCap":2800000000000,"price":182.5}]
            """;

    private static final String INCOME_AAPL = """
            [{"symbol":"AAPL","date":"2023-09-30","revenue":383285000000,
              "netIncome":96995000000,"operatingIncome":114301000000,
              "grossProfit":169148000000,"eps":6.16,"epsdiluted":6.13}]
            """;

    private static final String BALANCE_AAPL = """
            [{"symbol":"AAPL","date":"2023-09-30","totalAssets":352583000000,
              "totalLiabilities":290437000000,"totalEquity":62146000000,
              "totalDebt":111088000000,"cashAndShortTermInvestments":29965000000,
              "sharesOutstanding":15552752000}]
            """;

    private static final String CASHFLOW_AAPL = """
            [{"symbol":"AAPL","date":"2023-09-30","operatingCashFlow":110543000000,
              "capitalExpenditure":-11085000000,"freeCashFlow":99458000000}]
            """;

    private static final String RATIOS_AAPL = """
            [{"symbol":"AAPL","date":"2023-09-30","peRatio":29.5,"priceToBookRatio":46.2,
              "returnOnEquity":1.56,"returnOnAssets":0.28,"returnOnCapitalEmployed":0.35,
              "debtToEquity":1.79,"currentRatio":0.99,"dividendYield":0.0051,
              "payoutRatio":0.154,"netProfitMargin":0.253,
              "operatingProfitMargin":0.298,"grossProfitMargin":0.441}]
            """;

    private static final String QUOTE_AAPL = """
            [{"symbol":"AAPL","name":"Apple Inc.","price":182.5,"change":0.91,"changesPercentage":0.5}]
            """;

    // -----------------------------------------------------------------------
    // Happy path tests
    // -----------------------------------------------------------------------

    @Test
    void getProfile_returnsMappedCompanyProfile() {
        wireMock.stubFor(get(urlPathEqualTo("/profile"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .willReturn(okJson(PROFILE_AAPL)));

        CompanyProfile result = client.getProfile("AAPL");

        assertThat(result.symbol()).isEqualTo("AAPL");
        assertThat(result.companyName()).isEqualTo("Apple Inc.");
        assertThat(result.sector()).isEqualTo("Technology");
        assertThat(result.currency()).isEqualTo("USD");
    }

    @Test
    void getQuote_returnsMappedPriceQuote() {
        wireMock.stubFor(get(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .willReturn(okJson(QUOTE_AAPL)));

        MarketPriceQuote result = client.getQuote("AAPL");

        assertThat(result.symbol()).isEqualTo("AAPL");
        assertThat(result.price()).isEqualByComparingTo("182.5");
        assertThat(result.change()).isEqualByComparingTo("0.91");
    }

    @Test
    void getRatios_returnsMappedRatioSnapshot() {
        wireMock.stubFor(get(urlPathEqualTo("/ratios"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .willReturn(okJson(RATIOS_AAPL)));

        RatioSnapshot result = client.getRatios("AAPL");

        assertThat(result.symbol()).isEqualTo("AAPL");
        assertThat(result.peRatio()).isEqualByComparingTo("29.5");
        assertThat(result.roe()).isEqualByComparingTo("1.56");
    }

    @Test
    void getFundamentals_returnsMappedSnapshot() {
        wireMock.stubFor(get(urlPathEqualTo("/income-statement"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .willReturn(okJson(INCOME_AAPL)));
        wireMock.stubFor(get(urlPathEqualTo("/balance-sheet-statement"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .willReturn(okJson(BALANCE_AAPL)));
        wireMock.stubFor(get(urlPathEqualTo("/cash-flow-statement"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .willReturn(okJson(CASHFLOW_AAPL)));
        wireMock.stubFor(get(urlPathEqualTo("/profile"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .willReturn(okJson(PROFILE_AAPL)));

        FundamentalSnapshot result = client.getFundamentals("AAPL");

        assertThat(result.symbol()).isEqualTo("AAPL");
        assertThat(result.companyName()).isEqualTo("Apple Inc.");
        assertThat(result.epsTtm()).isEqualByComparingTo("6.13");
        assertThat(result.revenueHistory()).hasSize(1);
        assertThat(result.fcfHistory()).hasSize(1);
    }

    @Test
    void apikey_header_is_present_on_every_request() {
        wireMock.stubFor(get(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .willReturn(okJson(QUOTE_AAPL)));

        client.getQuote("AAPL");

        wireMock.verify(getRequestedFor(urlPathEqualTo("/quote"))
                .withHeader("apikey", equalTo("test-key")));
    }

    // -----------------------------------------------------------------------
    // Error path tests
    // -----------------------------------------------------------------------

    @Test
    void getProfile_whenNotFound_throwsMarketDataExceptionNotFound() {
        wireMock.stubFor(get(urlPathEqualTo("/profile"))
                .withQueryParam("symbol", equalTo("UNKNOWN"))
                .willReturn(aResponse().withStatus(404).withBody("[]")));

        assertThatThrownBy(() -> client.getProfile("UNKNOWN"))
                .isInstanceOf(MarketDataException.class)
                .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                        .isEqualTo(MarketDataException.ErrorCode.NOT_FOUND));
    }

    @Test
    void getQuote_whenEmptyResponse_throwsMarketDataExceptionNotFound() {
        wireMock.stubFor(get(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo("EMPTY"))
                .willReturn(okJson("[]")));

        assertThatThrownBy(() -> client.getQuote("EMPTY"))
                .isInstanceOf(MarketDataException.class)
                .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                        .isEqualTo(MarketDataException.ErrorCode.NOT_FOUND));
    }

    @Test
    void getQuote_when503_throwsServiceUnavailableAfterRetries() {
        wireMock.stubFor(get(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo("DOWN"))
                .willReturn(aResponse().withStatus(503).withBody("")));

        assertThatThrownBy(() -> client.getQuote("DOWN"))
                .isInstanceOf(MarketDataException.class)
                .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                        .isEqualTo(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE));

        // 1 initial attempt + 3 retries = 4 total calls
        wireMock.verify(4, getRequestedFor(urlPathEqualTo("/quote"))
                .withQueryParam("symbol", equalTo("DOWN")));
    }

    @Test
    void getProfile_whenFirstTwo429ThenSuccess_retriesAndReturnsResult() {
        wireMock.stubFor(get(urlPathEqualTo("/profile"))
                .withQueryParam("symbol", equalTo("RETRY"))
                .inScenario("profile-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("second-attempt")
                .willReturn(aResponse().withStatus(429).withBody("")));

        wireMock.stubFor(get(urlPathEqualTo("/profile"))
                .withQueryParam("symbol", equalTo("RETRY"))
                .inScenario("profile-retry")
                .whenScenarioStateIs("second-attempt")
                .willSetStateTo("third-attempt")
                .willReturn(aResponse().withStatus(429).withBody("")));

        wireMock.stubFor(get(urlPathEqualTo("/profile"))
                .withQueryParam("symbol", equalTo("RETRY"))
                .inScenario("profile-retry")
                .whenScenarioStateIs("third-attempt")
                .willReturn(okJson("""
                        [{"symbol":"RETRY","companyName":"Retry Corp","sector":"Tech",
                          "industry":"Software","country":"US","currency":"USD",
                          "exchangeShortName":"NASDAQ","mktCap":1000000000,"price":50.0}]
                        """)));

        CompanyProfile result = client.getProfile("RETRY");

        assertThat(result.symbol()).isEqualTo("RETRY");
        assertThat(result.companyName()).isEqualTo("Retry Corp");
        wireMock.verify(3, getRequestedFor(urlPathEqualTo("/profile"))
                .withQueryParam("symbol", equalTo("RETRY")));
    }
}
