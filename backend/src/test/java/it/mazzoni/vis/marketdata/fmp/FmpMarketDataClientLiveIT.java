package it.mazzoni.vis.marketdata.fmp;

import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.marketdata.fmp.dto.FmpInsiderTradingEntry;
import it.mazzoni.vis.marketdata.fmp.dto.FmpStockListEntry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live integration tests against the real FMP API.
 * Excluded from the default test run — requires application-fmpkey.yml (gitignored).
 *
 * Prerequisites:
 *   backend/src/test/resources/application-fmpkey.yml must exist with a valid fmp.api-key.
 *
 * Run with: mvn test -Pintegration-test
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.cache.type=none")
@ActiveProfiles({"test", "fmpkey"})
class FmpMarketDataClientLiveIT {

    static final KeyPair KEY_PAIR;

    static {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KEY_PAIR = gen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void jwtProps(DynamicPropertyRegistry registry) {
        Base64.Encoder enc = Base64.getEncoder();
        registry.add("jwt.private-key", () ->
                "-----BEGIN PRIVATE KEY-----\n" + enc.encodeToString(KEY_PAIR.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----");
        registry.add("jwt.public-key", () ->
                "-----BEGIN PUBLIC KEY-----\n" + enc.encodeToString(KEY_PAIR.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----");
    }

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    FmpMarketDataClient client;

    // --- Core endpoints (always accessible) ---

    @Test
    void getProfile_returnsAaplIdentity() {
        CompanyProfile profile = client.getProfile("AAPL");

        assertThat(profile.symbol()).isEqualTo("AAPL");
        assertThat(profile.companyName()).isNotBlank();
        assertThat(profile.sector()).isNotBlank();
        assertThat(profile.currency()).isEqualTo("USD");
    }

    @Test
    void getQuote_returnsPositiveAaplPrice() {
        MarketPriceQuote quote = client.getQuote("AAPL");

        assertThat(quote.symbol()).isEqualTo("AAPL");
        assertThat(quote.price()).isPositive();
    }

    @Test
    void getRatios_returnsMappedAaplRatios() {
        RatioSnapshot ratios = client.getRatios("AAPL");

        assertThat(ratios.symbol()).isEqualTo("AAPL");
        // At least one core ratio must be populated for a large-cap like AAPL
        boolean anyRatioPresent = ratios.peRatio() != null
                || ratios.roe() != null
                || ratios.priceToBook() != null;
        assertThat(anyRatioPresent)
                .as("Expected at least one ratio (PE, ROE, P/B) to be non-null for AAPL")
                .isTrue();
    }

    @Test
    void getFundamentals_returnsAaplRevenue() {
        FundamentalSnapshot snap = client.getFundamentals("AAPL");

        assertThat(snap.symbol()).isEqualTo("AAPL");
        assertThat(snap.companyName()).isNotBlank();
        assertThat(snap.revenueHistory()).isNotEmpty();
        assertThat(snap.revenueHistory().get(0)).isPositive();
    }

    // --- Extended endpoints (may require higher FMP plan) ---

    @Test
    void listSymbols_eitherReturnsNasdaqListOrServiceUnavailable() {
        // /stock-list is a bulk endpoint — may require a premium plan. FmpMarketDataClient.listSymbols
        // treats a plan-restricted/empty response as an empty list rather than an exception (see
        // UniverseSelectionService's fallback handling, which relies on this same contract), so an
        // empty result is an equally acceptable outcome here.
        try {
            List<FmpStockListEntry> symbols = client.listSymbols("NASDAQ");
            if (symbols.isEmpty()) {
                return;
            }
            assertThat(symbols).allSatisfy(e ->
                    assertThat(e.exchangeShortName()).isEqualToIgnoringCase("NASDAQ"));
            assertThat(symbols).anyMatch(e -> "AAPL".equals(e.symbol()));
        } catch (MarketDataException e) {
            // Acceptable: endpoint may not be in the current plan
            assertThat(e.getErrorCode()).isIn(
                    MarketDataException.ErrorCode.SERVICE_UNAVAILABLE,
                    MarketDataException.ErrorCode.NOT_FOUND);
        }
    }

    @Test
    void getDividendHistory_eitherReturnsHistoryOrNotFound() {
        // Premium plan endpoint — graceful failure is acceptable
        try {
            var history = client.getDividendHistory("KO");
            assertThat(history).isNotEmpty();
            assertThat(history.get(0).dividend()).isPositive();
        } catch (MarketDataException e) {
            assertThat(e.getErrorCode()).isIn(
                    MarketDataException.ErrorCode.NOT_FOUND,
                    MarketDataException.ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Test
    void getInsiderTransactions_eitherReturnsTradesOrNotFound() {
        // Premium plan endpoint — graceful failure is acceptable
        try {
            List<FmpInsiderTradingEntry> trades = client.getInsiderTransactions("AAPL");
            assertThat(trades).isNotEmpty();
            assertThat(trades.get(0).symbol()).isEqualToIgnoringCase("AAPL");
        } catch (MarketDataException e) {
            assertThat(e.getErrorCode()).isIn(
                    MarketDataException.ErrorCode.NOT_FOUND,
                    MarketDataException.ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Test
    void getFmpDcf_returnsEmptyOrPositiveValue() {
        // DCF endpoint may not be available on all plans — Optional.empty() is valid
        Optional<BigDecimal> dcf = client.getFmpDcf("AAPL");
        dcf.ifPresent(v -> assertThat(v).isPositive());
        // Either present+positive or empty — both are valid responses
    }
}
