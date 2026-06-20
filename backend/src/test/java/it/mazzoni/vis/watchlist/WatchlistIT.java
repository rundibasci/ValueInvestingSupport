package it.mazzoni.vis.watchlist;

import it.mazzoni.vis.auth.dto.LoginRequest;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.watchlist.dto.AddWatchlistItemRequest;
import it.mazzoni.vis.watchlist.dto.UpdateWatchlistThresholdRequest;
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
@ActiveProfiles("watchlist-test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WatchlistIT {

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
    UUID adminUserId;

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
        admin.setEmail("watchlist-admin@test.com");
        admin.setPasswordHash(passwordEncoder.encode("Password1!"));
        admin.setRole(UserRole.ADMIN);
        User saved = userRepository.save(admin);
        adminUserId = saved.getId();

        adminToken = login();
    }

    @Test
    void list_emptyWatchlist_returns200EmptyArray() {
        ResponseEntity<List<Map<String, Object>>> response = getList("/api/v1/watchlist");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void fullCrudFlow_works() {
        // Add AAPL
        AddWatchlistItemRequest addAapl = new AddWatchlistItemRequest("AAPL", null, null, null);
        ResponseEntity<Map<String, Object>> created = post("/api/v1/watchlist", addAapl);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("symbol")).isEqualTo("AAPL");
        assertThat(created.getBody().get("id")).isNotNull();
        assertThat(created.getBody().get("addedAt")).isNotNull();
        assertThat(created.getBody().get("mosAlertMin")).isNull();

        String aaplId = (String) created.getBody().get("id");

        // Duplicate add → 409
        ResponseEntity<String> duplicate = restTemplate.exchange(
                url("/api/v1/watchlist"), HttpMethod.POST,
                new HttpEntity<>(addAapl, jsonHeaders(adminToken)), String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // List → 1 item
        ResponseEntity<List<Map<String, Object>>> list = getList("/api/v1/watchlist");
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).hasSize(1);
        assertThat(list.getBody().get(0).get("symbol")).isEqualTo("AAPL");

        // Update thresholds
        UpdateWatchlistThresholdRequest update =
                new UpdateWatchlistThresholdRequest(new BigDecimal("10.0"), new BigDecimal("25.0"), null);
        ResponseEntity<Map<String, Object>> updated = restTemplate.exchange(
                url("/api/v1/watchlist/" + aaplId), HttpMethod.PUT,
                new HttpEntity<>(update, jsonHeaders(adminToken)),
                new ParameterizedTypeReference<>() {});
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) updated.getBody().get("mosAlertMin")).doubleValue()).isEqualTo(10.0);
        assertThat(((Number) updated.getBody().get("mosAlertMax")).doubleValue()).isEqualTo(25.0);

        // Add MSFT with fundamentalDegradeThreshold
        AddWatchlistItemRequest addMsft =
                new AddWatchlistItemRequest("MSFT", null, null, new BigDecimal("70.0"));
        ResponseEntity<Map<String, Object>> msft = post("/api/v1/watchlist", addMsft);
        assertThat(msft.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Number) msft.getBody().get("fundamentalDegradeThreshold")).doubleValue()).isEqualTo(70.0);

        // List → 2 items (newest first = MSFT)
        ResponseEntity<List<Map<String, Object>>> list2 = getList("/api/v1/watchlist");
        assertThat(list2.getBody()).hasSize(2);
        assertThat(list2.getBody().get(0).get("symbol")).isEqualTo("MSFT");

        // Delete AAPL
        ResponseEntity<Void> deleted = restTemplate.exchange(
                url("/api/v1/watchlist/" + aaplId), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(adminToken)), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // List → 1 item (only MSFT)
        ResponseEntity<List<Map<String, Object>>> list3 = getList("/api/v1/watchlist");
        assertThat(list3.getBody()).hasSize(1);
        assertThat(list3.getBody().get(0).get("symbol")).isEqualTo("MSFT");
    }

    @Test
    void delete_unknownId_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/v1/watchlist/" + UUID.randomUUID()), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(adminToken)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void alerts_withSeededActiveAlert_returns200WithAlert() {
        jdbc.update("""
                INSERT INTO alert(id, user_id, alert_type, symbol, threshold, status, triggered_at)
                VALUES (gen_random_uuid(), ?, 'MOS_ENTRY', 'KO', 15.0, 'ACTIVE', NOW())
                """, adminUserId);

        ResponseEntity<List<Map<String, Object>>> response = getList("/api/v1/watchlist/alerts");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        boolean hasKo = response.getBody().stream()
                .anyMatch(a -> "KO".equals(a.get("symbol")) && "MOS_ENTRY".equals(a.get("alertType")));
        assertThat(hasKo).isTrue();
    }

    @Test
    void unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/v1/watchlist"), HttpMethod.GET,
                HttpEntity.EMPTY, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- helpers ---

    private ResponseEntity<List<Map<String, Object>>> getList(String path) {
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
                new HttpEntity<>(new LoginRequest("watchlist-admin@test.com", "Password1!")),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
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
