package it.mazzoni.vis.thesis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Redis via Testcontainers, mirroring RedisCacheContractIT's convention — the
 * TTL-counter logic (INCR/EXPIRE-to-next-UTC-midnight) is exactly the kind of thing a fake
 * in-memory map would get subtly wrong. */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {"market-data.source=fmp", "fmp.api-key=test"})
@ActiveProfiles("demo")
class ThesisRateLimiterIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("thesis.daily-limit", () -> 2);
    }

    @Autowired StringRedisTemplate redis;
    @Autowired ThesisProperties properties;

    private ThesisRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new ThesisRateLimiter(redis, properties);
    }

    @Test
    void allowsUpToDailyLimit_thenRejects() {
        UUID userId = UUID.randomUUID();
        limiter.checkAndConsume(userId);
        limiter.checkAndConsume(userId);

        assertThatThrownBy(() -> limiter.checkAndConsume(userId))
                .isInstanceOf(ThesisRateLimitExceededException.class);
    }

    @Test
    void differentUsers_haveIndependentQuotas() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        limiter.checkAndConsume(userA);
        limiter.checkAndConsume(userA);

        // userA is now exhausted; userB must be unaffected.
        limiter.checkAndConsume(userB);
        assertThatThrownBy(() -> limiter.checkAndConsume(userA))
                .isInstanceOf(ThesisRateLimitExceededException.class);
    }

    @Test
    void setsExpiryOnFirstIncrement_soCounterResetsAtNextUtcMidnight() {
        UUID userId = UUID.randomUUID();
        limiter.checkAndConsume(userId);

        String key = "thesis:daily-limit:" + userId + ":" + LocalDate.now(ZoneOffset.UTC);
        Long ttl = redis.getExpire(key);

        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0);
    }
}
