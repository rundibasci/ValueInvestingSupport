package it.mazzoni.vis.pipeline;

import it.mazzoni.vis.auth.dto.LoginRequest;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.pipeline.dto.PipelineRunRequest;
import it.mazzoni.vis.pipeline.dto.PipelineRunResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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

/**
 * Live integration test against the real FMP API (Score1 + Score2 validation).
 * Excluded from the default test run — requires application-fmpkey.yml (gitignored).
 *
 * Prerequisites:
 *   backend/src/test/resources/application-fmpkey.yml with a valid fmp.api-key.
 *
 * Run with: mvn test -Pintegration-test
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "fmpkey"})
class PipelineDemoIT {

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
    StringRedisTemplate redisTemplate;

    @LocalServerPort int port;
    @Autowired TestRestTemplate restTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserRepository userRepository;
    @Autowired SecurityRepository securityRepository;
    @Autowired FundamentalSnapshotRepository fundamentalSnapshotRepository;
    @Autowired PriceQuoteRepository priceQuoteRepository;
    @Autowired ValuationResultRepository valuationResultRepository;
    @Autowired ValueScoreRepository valueScoreRepository;

    final Map<String, String> tokenStore = new ConcurrentHashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        valueScoreRepository.deleteAll();
        valuationResultRepository.deleteAll();
        priceQuoteRepository.deleteAll();
        fundamentalSnapshotRepository.deleteAll();
        securityRepository.deleteAll();
        userRepository.deleteAll();

        tokenStore.clear();
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doAnswer(inv -> tokenStore.put(inv.getArgument(0), inv.getArgument(1)))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));
        Mockito.when(valueOps.get(anyString()))
                .thenAnswer(inv -> tokenStore.get(inv.getArgument(0)));
        Mockito.when(redisTemplate.delete(anyString()))
                .thenAnswer(inv -> tokenStore.remove(inv.getArgument(0)) != null);

        User admin = new User();
        admin.setEmail("admin@test.com");
        admin.setPasswordHash(passwordEncoder.encode("Password1!"));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
    }

    @AfterEach
    void tearDown() {
        valueScoreRepository.deleteAll();
        valuationResultRepository.deleteAll();
        priceQuoteRepository.deleteAll();
        fundamentalSnapshotRepository.deleteAll();
        securityRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void pipelineRun_aaplFullPipeline_seedValueateScoreRank() {
        String token = login();
        HttpHeaders headers = bearerHeaders(token);

        PipelineRunRequest request = new PipelineRunRequest(List.of("AAPL"));
        ResponseEntity<List<PipelineRunResult>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/admin/pipeline-run",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<PipelineRunResult> results = response.getBody();
        assertThat(results).hasSize(1);

        PipelineRunResult result = results.get(0);
        assertThat(result.symbol()).isEqualTo("AAPL");
        assertThat(result.error()).isNull();
        assertThat(result.totalScore()).isNotNull().isPositive();
        assertThat(result.marginOfSafety()).isNotNull();
        assertThat(result.recommendation()).isNotNull().doesNotStartWith("ERROR");
    }

    private String login() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "http://localhost:" + port + "/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("admin@test.com", "Password1!")),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
