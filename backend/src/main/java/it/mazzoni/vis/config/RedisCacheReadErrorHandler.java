package it.mazzoni.vis.config;

import it.mazzoni.vis.observability.ObservabilitySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class RedisCacheReadErrorHandler implements CacheErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheReadErrorHandler.class);
    private final ObservabilitySupport observability;

    RedisCacheReadErrorHandler(ObservabilitySupport observability) {
        this.observability = observability;
    }

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        if (!causedBySerialization(exception)) throw exception;
        try {
            cache.evict(key);
        } catch (RuntimeException evictionError) {
            exception.addSuppressed(evictionError);
            throw exception;
        }
        String category = ObservabilitySupport.safeError(root(exception));
        observability.count("vis.cache.read.recovered",
                observability.tags("cache", cache.getName(), "error", category));
        log.warn("cache_read_recovered cache={} keyHash={} error={} action=evict_entry",
                cache.getName(), hash(key), category);
    }

    @Override public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        throw exception;
    }
    @Override public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        throw exception;
    }
    @Override public void handleCacheClearError(RuntimeException exception, Cache cache) {
        throw exception;
    }

    private static boolean causedBySerialization(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SerializationException
                    || current.getClass().getName().startsWith("com.fasterxml.jackson")) return true;
            current = current.getCause();
        }
        return false;
    }

    private static Throwable root(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }

    private static String hash(Object key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(key).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (Exception ignored) {
            return "unavailable";
        }
    }
}
