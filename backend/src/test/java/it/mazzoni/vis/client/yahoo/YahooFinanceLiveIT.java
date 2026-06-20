package it.mazzoni.vis.client.yahoo;

import it.mazzoni.vis.client.yahoo.dto.ChartResponse;
import it.mazzoni.vis.client.yahoo.dto.QuoteSummaryResponse;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the real Yahoo Finance API. Excluded from the default test run.
 * Run with: mvn test -Pintegration-test
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class YahooFinanceLiveIT {

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
    private YahooFinanceClient client;

    @Test
    void fetchAaplQuoteSummaryReturnsValidData() {
        QuoteSummaryResponse response = client.getQuoteSummary("AAPL");

        assertThat(response.quoteSummary().result()).isNotEmpty();
        assertThat(response.quoteSummary().result().get(0).financialData().currentPrice().raw())
                .isPositive();
        assertThat(response.quoteSummary().result().get(0).incomeStatementHistory().entries())
                .isNotEmpty();
    }

    @Test
    void fetchAaplChartReturnsRegularMarketPrice() {
        ChartResponse response = client.getChart("AAPL");

        assertThat(response.chart().result()).isNotEmpty();
        assertThat(response.chart().result().get(0).meta().regularMarketPrice())
                .isPositive();
        assertThat(response.chart().result().get(0).meta().symbol())
                .isEqualToIgnoringCase("AAPL");
    }
}
