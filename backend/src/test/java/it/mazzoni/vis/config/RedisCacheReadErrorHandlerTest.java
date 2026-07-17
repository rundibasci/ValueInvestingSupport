package it.mazzoni.vis.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import it.mazzoni.vis.observability.ObservabilitySupport;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.serializer.SerializationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisCacheReadErrorHandlerTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RedisCacheReadErrorHandler handler =
            new RedisCacheReadErrorHandler(new ObservabilitySupport(registry));

    @Test
    void serializationReadError_evictsOnlyFailingEntryAndRecordsRecovery() {
        ConcurrentMapCache cache = new ConcurrentMapCache("yahoo-chart");
        cache.put("bad", "broken");
        cache.put("good", "valid");

        handler.handleCacheGetError(new SerializationException("incompatible"), cache, "bad");

        assertThat(cache.get("bad")).isNull();
        assertThat(cache.get("good")).isNotNull();
        assertThat(registry.get("vis.cache.read.recovered").counter().count()).isEqualTo(1.0);
    }

    @Test
    void nonSerializationReadError_isNotSwallowed() {
        ConcurrentMapCache cache = new ConcurrentMapCache("yahoo-chart");
        IllegalStateException failure = new IllegalStateException("connection unavailable");

        assertThatThrownBy(() -> handler.handleCacheGetError(failure, cache, "key"))
                .isSameAs(failure);
    }

    @Test
    void writeErrorsAreNeverSwallowed() {
        ConcurrentMapCache cache = new ConcurrentMapCache("yahoo-chart");
        SerializationException failure = new SerializationException("cannot write");

        assertThatThrownBy(() -> handler.handleCachePutError(failure, cache, "key", "value"))
                .isSameAs(failure);
    }
}
