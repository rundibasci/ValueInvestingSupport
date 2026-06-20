package it.mazzoni.vis.screener;

import it.mazzoni.vis.auth.dto.LoginRequest;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.screener.dto.ScreenerRequest;
import it.mazzoni.vis.screener.dto.ScreenerResponse;
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

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * Full screener integration test against a real PostgreSQL container.
 * Seeds 5 000 rows, validates query correctness and the < 500 ms performance target.
 *
 * Run with: mvn test -Pintegration-test
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("screener-test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScreenerIT {

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
        // Mock Redis token store
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doAnswer(inv -> tokenStore.put(inv.getArgument(0), inv.getArgument(1)))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));
        Mockito.when(valueOps.get(anyString()))
                .thenAnswer(inv -> tokenStore.get(inv.getArgument(0)));
        Mockito.when(redisTemplate.delete(anyString()))
                .thenAnswer(inv -> tokenStore.remove(inv.getArgument(0)) != null);

        // Create admin user
        userRepository.deleteAll();
        User admin = new User();
        admin.setEmail("screener-admin@test.com");
        admin.setPasswordHash(passwordEncoder.encode("Password1!"));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        // Seed 5 000 securities with related data
        seed5000Rows();

        adminToken = login();
    }

    @Test
    void screen_noFilters_returns5000TotalAndDefaultSortDescByScore() {
        ScreenerResponse result = callScreener(emptyRequest());

        assertThat(result.totalElements()).isEqualTo(5000L);
        assertThat(result.results()).hasSize(20);

        // First item should have the highest totalScore on the page
        BigDecimal first = result.results().get(0).totalScore();
        BigDecimal last = result.results().get(result.results().size() - 1).totalScore();
        assertThat(first).isGreaterThanOrEqualTo(last);
    }

    @Test
    void screen_minValueScore80_allResultsAboveThreshold() {
        ScreenerRequest req = new ScreenerRequest(
                null, null, null, null,
                new BigDecimal("80"),
                null, null, null, null,
                "totalScore", "DESC", 0, 20);

        ScreenerResponse result = callScreener(req);
        assertThat(result.results()).allSatisfy(item ->
                assertThat(item.totalScore()).isGreaterThanOrEqualTo(new BigDecimal("80")));
    }

    @Test
    void screen_sectorFilter_allResultsMatchSector() {
        ScreenerRequest req = new ScreenerRequest(
                "Technology", null, null, null, null,
                null, null, null, null,
                "totalScore", "DESC", 0, 20);

        ScreenerResponse result = callScreener(req);
        assertThat(result.results()).allSatisfy(item ->
                assertThat(item.sector()).isEqualTo("Technology"));
    }

    @Test
    void screen_grahamPreset_allResultsMatchFilters() {
        ScreenerRequest req = new ScreenerRequest(
                null, null,
                new BigDecimal("15"), null,
                null,
                new BigDecimal("10"), new BigDecimal("1.0"),
                null, null,
                "totalScore", "DESC", 0, 20);

        ScreenerResponse result = callScreener(req);
        assertThat(result.results()).allSatisfy(item -> {
            assertThat(item.marginOfSafety()).isGreaterThanOrEqualTo(new BigDecimal("15"));
        });
    }

    @Test
    void screen_pagination_correctResultsPerPage() {
        ScreenerRequest page0 = new ScreenerRequest(
                null, null, null, null, null, null, null, null, null,
                "totalScore", "DESC", 0, 10);
        ScreenerRequest page1 = new ScreenerRequest(
                null, null, null, null, null, null, null, null, null,
                "totalScore", "DESC", 1, 10);

        ScreenerResponse r0 = callScreener(page0);
        ScreenerResponse r1 = callScreener(page1);

        assertThat(r0.results()).hasSize(10);
        assertThat(r1.results()).hasSize(10);
        assertThat(r0.results().get(0).symbol()).isNotEqualTo(r1.results().get(0).symbol());
    }

    @Test
    void screen_sectors_returnsNonEmptyList() {
        ResponseEntity<List<String>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/screener/sectors",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody()).contains("Technology", "Consumer Staples");
    }

    @Test
    void screen_noFilters_completesUnder500ms() {
        Instant start = Instant.now();
        callScreener(emptyRequest());
        long ms = Duration.between(start, Instant.now()).toMillis();

        assertThat(ms).as("Screener query must complete in < 500 ms but took %d ms", ms)
                .isLessThan(500L);
    }

    @Test
    void screen_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/screener",
                HttpMethod.POST,
                new HttpEntity<>(emptyRequest()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- helpers ---

    private ScreenerResponse callScreener(ScreenerRequest request) {
        ResponseEntity<ScreenerResponse> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/screener",
                HttpMethod.POST,
                new HttpEntity<>(request, bearerHeaders(adminToken)),
                ScreenerResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ScreenerRequest emptyRequest() {
        return new ScreenerRequest(
                null, null, null, null, null, null, null, null, null,
                "totalScore", "DESC", 0, 20);
    }

    private String login() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "http://localhost:" + port + "/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("screener-admin@test.com", "Password1!")),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private void seed5000Rows() {
        String today = java.time.LocalDate.now().toString();
        String now = java.time.LocalDateTime.now().toString();

        // Sectors and exchanges to spread across rows
        String[] sectors = {"Technology", "Consumer Staples", "Healthcare", "Energy", "Financials"};
        String[] exchanges = {"NYSE", "NASDAQ"};

        for (int i = 1; i <= 5000; i++) {
            String sym = String.format("T%04d", i);
            String sector = sectors[i % sectors.length];
            String exchange = exchanges[i % exchanges.length];
            // totalScore varies 0–100; marginOfSafety varies 0–40; ROIC varies 0–0.25; D/E 0–3
            double totalScore = (i % 101);
            double mos = (i % 41);
            double roic = (i % 26) / 100.0;
            double dte = (i % 31) / 10.0;
            double yield = (i % 5 == 0) ? 0.03 : 0.005;

            jdbc.update("""
                    INSERT INTO security(id, symbol, company_name, sector, exchange, created_at, updated_at)
                    VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?)
                    """, sym, "Company " + sym, sector, exchange, now, now);

            jdbc.update("""
                    INSERT INTO value_score(id, security_id, score_date, mos_score, quality_score,
                        safety_score, growth_score, dividend_score, total_score)
                    SELECT gen_random_uuid(), id, ?, 0, 0, 0, 0, 0, ?
                    FROM security WHERE symbol = ?
                    """, today, totalScore, sym);

            jdbc.update("""
                    INSERT INTO valuation_result(id, security_id, valuation_date,
                        composite_fair_value, current_price, margin_of_safety,
                        recommendation, source)
                    SELECT gen_random_uuid(), id, ?, 100.0, 80.0, ?, 'QUALITY_VALUE', 'test'
                    FROM security WHERE symbol = ?
                    """, today, mos, sym);

            jdbc.update("""
                    INSERT INTO ratio_snapshot(id, security_id, period, report_date,
                        roic, debt_to_equity, dividend_yield)
                    SELECT gen_random_uuid(), id, 'TTM', ?, ?, ?, ?
                    FROM security WHERE symbol = ?
                    """, today, roic, dte, yield, sym);
        }
    }
}
