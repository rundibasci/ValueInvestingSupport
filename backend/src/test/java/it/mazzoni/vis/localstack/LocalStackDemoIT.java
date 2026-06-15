package it.mazzoni.vis.localstack;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Phase LS1 — Local Stack Demo.
 *
 * Prerequisites: Redis must be running on localhost:6379.
 * Start it with: docker compose -f docker-compose.demo.yml up -d
 *
 * Excluded from the default mvn test run (@Tag("integration")).
 * Run with: mvn verify -Dgroups=integration
 */
@Tag("integration")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("localstack")
class LocalStackDemoIT {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Autowired
    TestRestTemplate rest;

    @Test
    void healthReturnsUp() {
        ResponseEntity<Map<String, Object>> response =
                rest.exchange("/actuator/health", HttpMethod.GET, null, MAP_TYPE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");

        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
        assertThat(components).containsKey("db");
        assertThat(components).containsKey("redis");

        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) components.get("db");
        assertThat(db).containsEntry("status", "UP");

        @SuppressWarnings("unchecked")
        Map<String, Object> redis = (Map<String, Object>) components.get("redis");
        assertThat(redis).containsEntry("status", "UP");
    }

    @Test
    void loginAndPingAdmin() {
        // Step 1: Login as seeded admin user
        Map<String, String> loginBody = Map.of(
                "email", "admin@localstack.local",
                "password", "admin"
        );
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map<String, Object>> loginResponse = rest.exchange(
                "/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(loginBody, loginHeaders),
                MAP_TYPE
        );

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String accessToken = (String) loginResponse.getBody().get("accessToken");
        assertThat(accessToken).isNotBlank();

        // Step 2: Call protected admin endpoint with JWT
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);

        ResponseEntity<Map<String, Object>> pingResponse = rest.exchange(
                "/api/v1/admin/ping",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                MAP_TYPE
        );

        assertThat(pingResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pingResponse.getBody()).containsEntry("status", "ok");
        assertThat(pingResponse.getBody()).containsEntry("role", "ADMIN");
    }
}
