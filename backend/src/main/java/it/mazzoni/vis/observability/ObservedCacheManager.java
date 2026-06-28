package it.mazzoni.vis.observability;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ObservedCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final ObservabilitySupport observability;
    private final Map<String, Cache> observedCaches = new ConcurrentHashMap<>();

    public ObservedCacheManager(CacheManager delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = delegate.getCache(name);
        if (cache == null) {
            return null;
        }
        return observedCaches.computeIfAbsent(name, ignored -> new ObservedCache(cache, observability));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }
}
