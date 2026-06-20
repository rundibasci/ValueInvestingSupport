package it.mazzoni.vis.marketdata;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end fallback integration test.
 * FMP is replaced by WireMock; Yahoo Finance is called for real on plan-restriction (402).
 *
 * Run with: mvn test -Pintegration-test
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.cache.type=none")
@ActiveProfiles("test")
class FmpWithYahooFallbackLiveIT {

    static final KeyPair KEY_PAIR;
    static WireMockServer wireMock;

    static {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KEY_PAIR = gen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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
    static void props(DynamicPropertyRegistry registry) {
        registry.add("fmp.base-url", () -> "http://localhost:" + wireMock.port());
        Base64.Encoder enc = Base64.getEncoder();
        registry.add("jwt.private-key", () ->
                "-----BEGIN PRIVATE KEY-----\n" + enc.encodeToString(KEY_PAIR.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----");
        registry.add("jwt.public-key", () ->
                "-----BEGIN PUBLIC KEY-----\n" + enc.encodeToString(KEY_PAIR.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----");
    }

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    MarketDataClient client; // resolves to FmpWithYahooFallbackMarketDataClient (primary when source=fmp)

    private static final String AAPL = "AAPL";

    // -----------------------------------------------------------------------
    // Fallback: FMP returns 402 → Yahoo Finance must supply real data
    // -----------------------------------------------------------------------

    @Test
    void getQuote_whenFmpReturns402_returnsYahooPrice() {
        wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(402).withBody("")));

        MarketPriceQuote quote = client.getQuote(AAPL);

        assertThat(quote.symbol()).isEqualToIgnoringCase(AAPL);
        assertThat(quote.price()).isPositive();
    }

    @Test
    void getProfile_whenFmpReturns402_returnsYahooCompanyProfile() {
        wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(402).withBody("")));

        CompanyProfile profile = client.getProfile(AAPL);

        assertThat(profile.symbol()).isEqualTo(AAPL);
        assertThat(profile.companyName()).isNotBlank();
    }

    @Test
    void getRatios_whenFmpReturns402_returnsYahooRatioSnapshot() {
        wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(402).withBody("")));

        RatioSnapshot ratios = client.getRatios(AAPL);

        assertThat(ratios.symbol()).isEqualTo(AAPL);
        boolean anyRatio = ratios.peRatio() != null || ratios.priceToBook() != null || ratios.roe() != null;
        assertThat(anyRatio).as("Expected at least one ratio from Yahoo Finance for AAPL").isTrue();
    }

    @Test
    void getFundamentals_whenFmpReturns402_returnsYahooFundamentals() {
        wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(402).withBody("")));

        FundamentalSnapshot snap = client.getFundamentals(AAPL);

        assertThat(snap.symbol()).isEqualTo(AAPL);
        assertThat(snap.revenueHistory()).isNotEmpty();
        assertThat(snap.revenueHistory().get(0)).isPositive();
    }

    // -----------------------------------------------------------------------
    // Non-fallback errors: FMP 404/503 must propagate; Yahoo must NOT be called
    // -----------------------------------------------------------------------

    @Test
    void getQuote_whenFmpReturns404_throwsNotFoundAndSkipsYahoo() {
        wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404).withBody("[]")));

        assertThatThrownBy(() -> client.getQuote(AAPL))
                .isInstanceOf(MarketDataException.class)
                .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                        .isEqualTo(MarketDataException.ErrorCode.NOT_FOUND));
    }

    @Test
    void getProfile_whenFmpReturns404_throwsNotFoundAndSkipsYahoo() {
        wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404).withBody("[]")));

        assertThatThrownBy(() -> client.getProfile(AAPL))
                .isInstanceOf(MarketDataException.class)
                .satisfies(e -> assertThat(((MarketDataException) e).getErrorCode())
                        .isEqualTo(MarketDataException.ErrorCode.NOT_FOUND));
    }
}
