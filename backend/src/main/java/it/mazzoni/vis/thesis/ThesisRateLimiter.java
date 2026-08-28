package it.mazzoni.vis.thesis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * {@code THESIS_GENERATION_DAILY_LIMIT} (default 5, specs/tech-stack.md), per authenticated
 * user, per UTC calendar day, applied identically to every role — no ADMIN bypass (TA1's
 * ADR-002 decision this class must not silently relax).
 *
 * <p>Redis {@code INCR} + {@code EXPIRE}-to-next-UTC-midnight on a per-user-per-day key. Not
 * perfectly atomic under extreme concurrency at the exact limit boundary — an accepted,
 * disclosed tradeoff at this default limit's low value (see requirements.md -> Compatibility
 * and Risks), not a promise of strict atomicity.
 */
@Component
public class ThesisRateLimiter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String KEY_PREFIX = "thesis:daily-limit:";

    private final StringRedisTemplate redis;
    private final ThesisProperties properties;

    public ThesisRateLimiter(StringRedisTemplate redis, ThesisProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /** Throws {@link ThesisRateLimitExceededException} if the caller has already reached
     * {@code THESIS_GENERATION_DAILY_LIMIT} for today (UTC); otherwise consumes one unit. */
    public void checkAndConsume(UUID userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String key = KEY_PREFIX + userId + ":" + today.format(DAY);

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, secondsUntilNextUtcMidnight());
        }

        int limit = properties.getDailyLimit();
        if (count != null && count > limit) {
            LocalDateTime resetsAt = LocalDateTime.of(today.plusDays(1), LocalTime.MIDNIGHT);
            throw new ThesisRateLimitExceededException(limit, resetsAt);
        }
    }

    private static Duration secondsUntilNextUtcMidnight() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, nextMidnight);
    }
}
