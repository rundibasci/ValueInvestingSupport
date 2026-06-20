package it.mazzoni.vis.security;

import it.mazzoni.vis.auth.dto.LoginRequest;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.security.dto.ValuationDetailResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("security-detail-test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityDetailIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

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
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        Base64.Encoder enc = Base64.getEncoder();
        registry.add("jwt.private-key", () ->
                "-----BEGIN PRIVATE KEY-----\n" + enc.encodeToString(KEY_PAIR.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----");
        registry.add("jwt.public-key", () ->
                "-----BEGIN PUBLIC KEY-----\n" + enc.encodeToString(KEY_PAIR.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----");
    }

    @MockitoBean
    StringRedisTemplate redisTemplate;

    @LocalServerPort int port;
    @Autowired TestRestTemplate restTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    final Map<String, String> tokenStore = new ConcurrentHashMap<>();
    String adminToken;

    @BeforeAll
    @SuppressWarnings("unchecked")
    void setUpOnce() {
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doAnswer(inv -> tokenStore.put(inv.getArgument(0), inv.getArgument(1)))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));
        Mockito.when(valueOps.get(anyString()))
                .thenAnswer(inv -> tokenStore.get(inv.getArgument(0)));
        Mockito.when(redisTemplate.delete(anyString()))
                .thenAnswer(inv -> tokenStore.remove(inv.getArgument(0)) != null);

        userRepository.deleteAll();
        User admin = new User();
        admin.setEmail("security-admin@test.com");
        admin.setPasswordHash(passwordEncoder.encode("Password1!"));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        seedTestData();

        adminToken = login();
    }

    // --- endpoint tests ---

    @Test
    void search_bySymbol_returns200WithResult() {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url("/api/v1/securities/search?q=AAPL"),
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().get(0).get("symbol")).isEqualTo("AAPL");
    }

    @Test
    void profile_knownSymbol_returns200WithCompanyName() {
        ResponseEntity<Map<String, Object>> response = get("/api/v1/securities/AAPL");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("symbol")).isEqualTo("AAPL");
        assertThat(response.getBody().get("companyName")).isEqualTo("Apple Inc.");
    }

    @Test
    void financials_knownSymbol_returns200WithAnnualsAndQuarters() {
        ResponseEntity<Map<String, Object>> response = get("/api/v1/securities/AAPL/financials");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("annuals")).isNotEmpty();
        assertThat((List<?>) response.getBody().get("quarters")).isNotEmpty();
    }

    @Test
    void ratios_knownSymbol_returns200WithRatioHistory() {
        ResponseEntity<Map<String, Object>> response = get("/api/v1/securities/AAPL/ratios");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("ratios")).isNotEmpty();
    }

    @Test
    void dividends_knownSymbol_returns200WithHistory() {
        ResponseEntity<Map<String, Object>> response = get("/api/v1/securities/AAPL/dividends");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("history")).isNotEmpty();
        assertThat((Integer) response.getBody().get("streak")).isGreaterThan(0);
    }

    @Test
    void insiders_knownSymbol_returns200WithTrades() {
        ResponseEntity<Map<String, Object>> response = get("/api/v1/securities/AAPL/insiders");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("trades")).isNotEmpty();
    }

    @Test
    void growth_knownSymbol_returns200WithCagr3y() {
        ResponseEntity<Map<String, Object>> response = get("/api/v1/securities/AAPL/growth");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("symbol")).isEqualTo("AAPL");
        Map<?, ?> revenue = (Map<?, ?>) response.getBody().get("revenue");
        assertThat(revenue.get("cagr3y")).isNotNull();
    }

    @Test
    void peers_knownSymbol_returns200() {
        ResponseEntity<Map<String, Object>> response = get("/api/v1/securities/AAPL/peers");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("symbol")).isEqualTo("AAPL");
    }

    @Test
    void valuation_knownSymbol_returns200WithDisclaimer() {
        ResponseEntity<Map<String, Object>> response = get("/api/v1/securities/AAPL/valuation");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("disclaimer")).isEqualTo(ValuationDetailResponse.MIFID_DISCLAIMER);
        assertThat(response.getBody().get("compositeFairValue")).isNotNull();
    }

    @Test
    void profile_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/v1/securities/AAPL"),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void profile_unknownSymbol_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/v1/securities/UNKNOWN"),
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- helpers ---

    private ResponseEntity<Map<String, Object>> get(String path) {
        return restTemplate.exchange(
                url(path),
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)),
                new ParameterizedTypeReference<>() {});
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String login() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url("/auth/login"),
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("security-admin@test.com", "Password1!")),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private void seedTestData() {
        String today = java.time.LocalDate.now().toString();
        String now = java.time.LocalDateTime.now().toString();

        // Securities
        jdbc.update("""
                INSERT INTO security(id, symbol, company_name, sector, exchange, market_cap, country, currency, created_at, updated_at)
                VALUES (gen_random_uuid(), 'AAPL', 'Apple Inc.', 'Technology', 'NASDAQ', 3000000000000.00, 'US', 'USD', ?, ?)
                """, now, now);
        jdbc.update("""
                INSERT INTO security(id, symbol, company_name, sector, exchange, market_cap, country, currency, created_at, updated_at)
                VALUES (gen_random_uuid(), 'MSFT', 'Microsoft Corp.', 'Technology', 'NASDAQ', 2800000000000.00, 'US', 'USD', ?, ?)
                """, now, now);

        // Annual fundamental snapshots for AAPL (5 years; most recent with report_date = today for stale check)
        for (int i = 0; i < 5; i++) {
            int year = 2025 - i;
            String reportDate = (i == 0) ? today : (year + "-09-30");
            jdbc.update("""
                    INSERT INTO fundamental_snapshot(id, security_id, period, fiscal_year, report_date,
                        revenue, net_income, eps, free_cash_flow, total_equity, shares_outstanding)
                    SELECT gen_random_uuid(), id, 'ANNUAL', ?, ?,
                        ?, ?, ?, ?, ?, ?
                    FROM security WHERE symbol = 'AAPL'
                    """,
                    year, reportDate,
                    100000000000L - (long) i * 5000000000L,
                    20000000000L - (long) i * 1000000000L,
                    6.43 - i * 0.3,
                    20000000000L - (long) i * 500000000L,
                    60000000000L,
                    15500000000L);
        }

        // Quarterly snapshot for AAPL
        jdbc.update("""
                INSERT INTO fundamental_snapshot(id, security_id, period, fiscal_year, fiscal_quarter, report_date,
                    revenue, net_income, eps, free_cash_flow)
                SELECT gen_random_uuid(), id, 'QUARTERLY', 2025, 2, ?, ?, ?, ?, ?
                FROM security WHERE symbol = 'AAPL'
                """, today, 25000000000L, 5000000000L, 1.60, 5000000000L);

        // TTM snapshot for AAPL
        jdbc.update("""
                INSERT INTO fundamental_snapshot(id, security_id, period, report_date,
                    revenue, net_income, eps, free_cash_flow)
                SELECT gen_random_uuid(), id, 'TTM', ?, ?, ?, ?, ?
                FROM security WHERE symbol = 'AAPL'
                """, today, 110000000000L, 22000000000L, 7.00, 22000000000L);

        // Ratio snapshot for AAPL
        jdbc.update("""
                INSERT INTO ratio_snapshot(id, security_id, period, report_date,
                    pe_ratio, roic, roe, debt_to_equity, gross_margin, dividend_yield)
                SELECT gen_random_uuid(), id, 'TTM', ?, ?, ?, ?, ?, ?, ?
                FROM security WHERE symbol = 'AAPL'
                """, today, 28.4, 26.4, 147.3, 1.87, 46.2, 0.56);

        // Price quote for AAPL
        jdbc.update("""
                INSERT INTO price_quote(id, security_id, quote_date, open, high, low, close, adjusted_close, volume)
                SELECT gen_random_uuid(), id, ?, 183.0, 188.0, 182.0, 185.0, 185.0, 50000000
                FROM security WHERE symbol = 'AAPL'
                """, today);

        // Valuation result for AAPL
        jdbc.update("""
                INSERT INTO valuation_result(id, security_id, valuation_date,
                    dcf_fair_value, dcf_fair_value_low, dcf_fair_value_high,
                    graham_number, ddm_fair_value, composite_fair_value,
                    current_price, margin_of_safety, recommendation, source)
                SELECT gen_random_uuid(), id, ?,
                    220.0, 200.0, 240.0,
                    180.0, 210.0, 220.0,
                    185.0, 18.92, 'QUALITY_VALUE', 'test'
                FROM security WHERE symbol = 'AAPL'
                """, today);

        // Value score for AAPL (used in peers view)
        jdbc.update("""
                INSERT INTO value_score(id, security_id, score_date,
                    mos_score, quality_score, safety_score, growth_score, dividend_score, total_score)
                SELECT gen_random_uuid(), id, ?, 20.0, 20.0, 15.0, 15.0, 10.0, 80.0
                FROM security WHERE symbol = 'AAPL'
                """, today);

        // Value score for MSFT (peer)
        jdbc.update("""
                INSERT INTO value_score(id, security_id, score_date,
                    mos_score, quality_score, safety_score, growth_score, dividend_score, total_score)
                SELECT gen_random_uuid(), id, ?, 18.0, 19.0, 15.0, 14.0, 10.0, 76.0
                FROM security WHERE symbol = 'MSFT'
                """, today);

        // Dividend records for AAPL (4 years — streak=4, cagr3y computable)
        for (int i = 0; i < 4; i++) {
            String exDate = (2025 - i) + "-08-09";
            double amount = 1.00 - i * 0.10;
            jdbc.update("""
                    INSERT INTO dividend_record(id, security_id, ex_dividend_date, amount, currency)
                    SELECT gen_random_uuid(), id, ?, ?, 'USD'
                    FROM security WHERE symbol = 'AAPL'
                    """, exDate, amount);
        }

        // Recent insider trade for AAPL
        jdbc.update("""
                INSERT INTO insider_trade(id, security_id, trade_date, insider_name, title,
                    transaction_type, shares, price_per_share, trade_value)
                SELECT gen_random_uuid(), id, ?, 'Tim Cook', 'CEO', 'SELL', 100000, 185.0, 18500000.00
                FROM security WHERE symbol = 'AAPL'
                """, today);
    }
}
