package it.mazzoni.vis.observability;

import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

public class ObservedCache implements Cache {

    private final Cache delegate;
    private final ObservabilitySupport observability;

    public ObservedCache(Cache delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper value = delegate.get(key);
        record(value == null ? "miss" : "hit");
        return value;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        T value = delegate.get(key, type);
        record(value == null ? "miss" : "hit");
        return value;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        final boolean[] loaded = {false};
        try {
            T value = delegate.get(key, () -> {
                loaded[0] = true;
                return valueLoader.call();
            });
            record(loaded[0] ? "miss" : "hit");
            return value;
        } catch (RuntimeException e) {
            record("error");
            throw e;
        }
    }

    @Override
    public void put(Object key, Object value) {
        delegate.put(key, value);
        record("put");
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper existing = delegate.putIfAbsent(key, value);
        record(existing == null ? "put" : "hit");
        return existing;
    }

    @Override
    public void evict(Object key) {
        delegate.evict(key);
        record("evict");
    }

    @Override
    public boolean evictIfPresent(Object key) {
        boolean evicted = delegate.evictIfPresent(key);
        record("evict");
        return evicted;
    }

    @Override
    public void clear() {
        delegate.clear();
        record("clear");
    }

    @Override
    public boolean invalidate() {
        boolean invalidated = delegate.invalidate();
        record("clear");
        return invalidated;
    }

    private void record(String outcome) {
        observability.count("vis.cache.access",
                observability.tags("cache", getName(), "outcome", outcome));
    }
}
