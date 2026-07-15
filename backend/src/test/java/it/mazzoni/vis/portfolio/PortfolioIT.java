package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.auth.dto.LoginRequest;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.portfolio.dto.AddHoldingRequest;
import it.mazzoni.vis.portfolio.dto.CreatePortfolioRequest;
import it.mazzoni.vis.portfolio.dto.UpdateHoldingRequest;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("portfolio-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortfolioIT {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

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
    UUID adminUserId;

    @BeforeEach
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
        admin.setEmail("portfolio-admin@test.com");
        admin.setPasswordHash(passwordEncoder.encode("Password1!"));
        admin.setRole(UserRole.ADMIN);
        User saved = userRepository.save(admin);
        adminUserId = saved.getId();

        adminToken = login();
    }

    @Test
    void list_noPortfolios_returns200EmptyArray() {
        ResponseEntity<List<Map<String, Object>>> response = getList("/api/v1/portfolios");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void fullCrudFlow_works() {
        // Create portfolio
        CreatePortfolioRequest createReq = new CreatePortfolioRequest("Growth Portfolio", "Long-term holds");
        ResponseEntity<Map<String, Object>> created = post("/api/v1/portfolios", createReq);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("name")).isEqualTo("Growth Portfolio");
        assertThat(created.getBody().get("id")).isNotNull();
        assertThat(((Number) created.getBody().get("holdingCount")).intValue()).isEqualTo(0);

        String portfolioId = (String) created.getBody().get("id");

        // List → 1 portfolio
        ResponseEntity<List<Map<String, Object>>> list = getList("/api/v1/portfolios");
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).hasSize(1);
        assertThat(list.getBody().get(0).get("name")).isEqualTo("Growth Portfolio");

        // Add AAPL holding (symbol lowercase → must be uppercased)
        AddHoldingRequest addAapl = new AddHoldingRequest("aapl", new BigDecimal("10"),
                new BigDecimal("150.00"), "USD");
        ResponseEntity<Map<String, Object>> h1 = post("/api/v1/portfolios/" + portfolioId + "/holdings", addAapl);
        assertThat(h1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(h1.getBody().get("symbol")).isEqualTo("AAPL");
        assertThat(((Number) h1.getBody().get("quantity")).doubleValue()).isEqualTo(10.0);
        assertThat(h1.getBody().get("currentPrice")).isNull();

        String holdingId1 = (String) h1.getBody().get("id");

        // Add MSFT holding
        AddHoldingRequest addMsft = new AddHoldingRequest("MSFT", new BigDecimal("5"), null, null);
        ResponseEntity<Map<String, Object>> h2 = post("/api/v1/portfolios/" + portfolioId + "/holdings", addMsft);
        assertThat(h2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(h2.getBody().get("symbol")).isEqualTo("MSFT");

        String holdingId2 = (String) h2.getBody().get("id");

        // Portfolio detail — no prices yet
        ResponseEntity<Map<String, Object>> detail = getOne("/api/v1/portfolios/" + portfolioId);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> holdings = (List<?>) detail.getBody().get("holdings");
        assertThat(holdings).hasSize(2);
        assertThat(detail.getBody().get("totalValue")).isNull();
        assertThat(detail.getBody().get("weightedMoS")).isNull();

        // Seed AAPL security + price quote + valuation result
        UUID securityId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO security(id, symbol, company_name, exchange, sector, created_at)
                VALUES (?, 'AAPL', 'Apple Inc.', 'NASDAQ', 'Technology', NOW())
                """, securityId);
        jdbc.update("""
                INSERT INTO price_quote(id, security_id, quote_date, close)
                VALUES (gen_random_uuid(), ?, CURRENT_DATE, 180.00)
                """, securityId);
        jdbc.update("""
                INSERT INTO valuation_result(id, security_id, valuation_date,
                    composite_fair_value, margin_of_safety, recommendation)
                VALUES (gen_random_uuid(), ?, CURRENT_DATE, 210.00, 16.67, 'QUALITY_VALUE')
                """, securityId);

        // Portfolio detail — AAPL now has price and valuation
        ResponseEntity<Map<String, Object>> detail2 = getOne("/api/v1/portfolios/" + portfolioId);
        assertThat(detail2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail2.getBody().get("totalValue")).isNotNull();
        assertThat(((Number) detail2.getBody().get("totalValue")).doubleValue()).isEqualTo(1800.0);

        List<Map<String, Object>> items = (List<Map<String, Object>>) detail2.getBody().get("holdings");
        // Newest first — MSFT added after AAPL
        Map<String, Object> msftItem = items.stream()
                .filter(i -> "MSFT".equals(i.get("symbol"))).findFirst().orElseThrow();
        Map<String, Object> aaplItem = items.stream()
                .filter(i -> "AAPL".equals(i.get("symbol"))).findFirst().orElseThrow();

        assertThat(((Number) aaplItem.get("currentPrice")).doubleValue()).isEqualTo(180.0);
        assertThat(((Number) aaplItem.get("currentValue")).doubleValue()).isEqualTo(1800.0);
        assertThat(((Number) aaplItem.get("weightPercent")).doubleValue()).isEqualTo(100.0);
        assertThat(((Number) aaplItem.get("compositeFairValue")).doubleValue()).isEqualTo(210.0);
        assertThat(((Number) aaplItem.get("marginOfSafety")).doubleValue()).isEqualTo(16.67);
        assertThat(aaplItem.get("recommendation")).isEqualTo("QUALITY_VALUE");
        assertThat(msftItem.get("currentPrice")).isNull();
        List<Map<String, Object>> warnings = (List<Map<String, Object>>) detail2.getBody().get("concentrationWarnings");
        assertThat(warnings).extracting(w -> w.get("type"))
                .contains("HOLDING", "SECTOR", "DATA_UNAVAILABLE");
        assertThat(warnings).anySatisfy(w -> {
            assertThat(w.get("type")).isEqualTo("HOLDING");
            assertThat(w.get("key")).isEqualTo("AAPL");
            assertThat(((Number) w.get("thresholdPercent")).doubleValue()).isEqualTo(20.0);
        });

        // Update AAPL holding
        UpdateHoldingRequest updateReq = new UpdateHoldingRequest(new BigDecimal("15"),
                new BigDecimal("145.00"), "USD");
        ResponseEntity<Map<String, Object>> updated = restTemplate.exchange(
                url("/api/v1/portfolios/" + portfolioId + "/holdings/" + holdingId1),
                HttpMethod.PUT,
                new HttpEntity<>(updateReq, jsonHeaders(adminToken)),
                new ParameterizedTypeReference<>() {});
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) updated.getBody().get("quantity")).doubleValue()).isEqualTo(15.0);
        assertThat(((Number) updated.getBody().get("averageCostBasis")).doubleValue()).isEqualTo(145.0);

        // Delete MSFT holding
        ResponseEntity<Void> deleted = restTemplate.exchange(
                url("/api/v1/portfolios/" + portfolioId + "/holdings/" + holdingId2),
                HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(adminToken)), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Portfolio detail — only AAPL, updated quantity
        ResponseEntity<Map<String, Object>> detail3 = getOne("/api/v1/portfolios/" + portfolioId);
        List<Map<String, Object>> finalHoldings = (List<Map<String, Object>>) detail3.getBody().get("holdings");
        assertThat(finalHoldings).hasSize(1);
        assertThat(finalHoldings.get(0).get("symbol")).isEqualTo("AAPL");
        assertThat(((Number) finalHoldings.get(0).get("quantity")).doubleValue()).isEqualTo(15.0);
        assertThat(((Number) detail3.getBody().get("totalValue")).doubleValue()).isEqualTo(2700.0);

        // Create second portfolio
        ResponseEntity<Map<String, Object>> second = post("/api/v1/portfolios",
                new CreatePortfolioRequest("Dividend Portfolio", null));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // List → 2 portfolios
        ResponseEntity<List<Map<String, Object>>> list2 = getList("/api/v1/portfolios");
        assertThat(list2.getBody()).hasSize(2);
    }

    @Test
    void detail_unknownPortfolio_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/v1/portfolios/" + UUID.randomUUID()), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void removeHolding_unknownId_returns404() {
        // Create a portfolio first to have a valid portfolio id
        ResponseEntity<Map<String, Object>> created = post("/api/v1/portfolios",
                new CreatePortfolioRequest("Temp", null));
        String portfolioId = (String) created.getBody().get("id");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/v1/portfolios/" + portfolioId + "/holdings/" + UUID.randomUUID()),
                HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(adminToken)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletePortfolio_removesScopedChildrenAndPreservesAuditAndOtherPortfolios() {
        ResponseEntity<Map<String, Object>> created = post("/api/v1/portfolios",
                new CreatePortfolioRequest("Delete me", null));
        UUID portfolioId = UUID.fromString((String) created.getBody().get("id"));
        post("/api/v1/portfolios/" + portfolioId + "/holdings",
                new AddHoldingRequest("KO", new BigDecimal("2"), null, "USD"));

        ResponseEntity<Map<String, Object>> retained = post("/api/v1/portfolios",
                new CreatePortfolioRequest("Keep me", null));
        UUID retainedPortfolioId = UUID.fromString((String) retained.getBody().get("id"));

        UUID proposalId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO rebalance_proposal(id, portfolio_id, status, holdings_fingerprint, created_at)
                VALUES (?, ?, 'PENDING', 'fingerprint', NOW())
                """, proposalId, portfolioId);
        jdbc.update("""
                INSERT INTO rebalance_line(id, proposal_id, symbol, captured_price, current_quantity, target_quantity)
                VALUES (?, ?, 'KO', 60.00, 2.00, 3.00)
                """, UUID.randomUUID(), proposalId);
        jdbc.update("""
                INSERT INTO portfolio_analytics_snapshot(
                    id, portfolio_id, captured_at, total_market_value, benchmark_symbol, warning_count, payload)
                VALUES (?, ?, NOW(), 120.00, 'SPY', 0, '{}')
                """, UUID.randomUUID(), portfolioId);
        UUID auditId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO research_snapshot(id, user_id, symbol, action_type, captured_at, rationale)
                VALUES (?, ?, 'KO', 'ADD_HOLDING', NOW(), 'Retained audit evidence')
                """, auditId, adminUserId);

        ResponseEntity<Void> response = restTemplate.exchange(
                url("/api/v1/portfolios/" + portfolioId), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(adminToken)), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(count("portfolio", "id", portfolioId)).isZero();
        assertThat(count("holding", "portfolio_id", portfolioId)).isZero();
        assertThat(count("rebalance_proposal", "portfolio_id", portfolioId)).isZero();
        assertThat(count("rebalance_line", "proposal_id", proposalId)).isZero();
        assertThat(count("portfolio_analytics_snapshot", "portfolio_id", portfolioId)).isZero();
        assertThat(count("portfolio", "id", retainedPortfolioId)).isOne();
        assertThat(count("research_snapshot", "id", auditId)).isOne();

        ResponseEntity<String> repeated = restTemplate.exchange(
                url("/api/v1/portfolios/" + portfolioId), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(adminToken)), String.class);
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/v1/portfolios"), HttpMethod.GET,
                HttpEntity.EMPTY, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- helpers ---

    private ResponseEntity<List<Map<String, Object>>> getList(String path) {
        return restTemplate.exchange(url(path), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)),
                new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<Map<String, Object>> getOne(String path) {
        return restTemplate.exchange(url(path), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)),
                new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<Map<String, Object>> post(String path, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders(adminToken)),
                new ParameterizedTypeReference<>() {});
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String login() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url("/auth/login"), HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("portfolio-admin@test.com", "Password1!")),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private long count(String table, String column, UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class, id);
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders h = bearerHeaders(token);
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return h;
    }
}
