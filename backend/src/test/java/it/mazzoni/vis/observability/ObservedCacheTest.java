package it.mazzoni.vis.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.assertj.core.api.Assertions.assertThat;

class ObservedCacheTest {

    @Test
    void get_recordsHitAndMissMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilitySupport observability = new ObservabilitySupport(registry);
        ObservedCache cache = new ObservedCache(new ConcurrentMapCache("mdc-quote"), observability);

        cache.get("missing");
        cache.put("present", "value");
        cache.get("present");

        assertThat(registry.find("vis.cache.access").tag("cache", "mdc-quote").tag("outcome", "miss").counter())
                .isNotNull();
        assertThat(registry.find("vis.cache.access").tag("cache", "mdc-quote").tag("outcome", "hit").counter())
                .isNotNull();
        assertThat(registry.find("vis.cache.access").tag("cache", "mdc-quote").tag("outcome", "put").counter())
                .isNotNull();
    }
}
