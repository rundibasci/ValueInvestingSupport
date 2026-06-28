package it.mazzoni.vis.marketdata;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class MarketDataStatusTracker {

    private final AtomicReference<String> lastProvider = new AtomicReference<>("none");
    private final AtomicReference<String> lastFallbackReason = new AtomicReference<>("none");
    private final AtomicReference<Instant> lastFallbackAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccessAt = new AtomicReference<>();

    public void recordSuccess(String provider) {
        lastProvider.set(provider);
        lastSuccessAt.set(Instant.now());
    }

    public void recordFallback(String reason) {
        lastProvider.set("yahoo");
        lastFallbackReason.set(reason);
        lastFallbackAt.set(Instant.now());
        lastSuccessAt.set(Instant.now());
    }

    public String lastProvider() {
        return lastProvider.get();
    }

    public String lastFallbackReason() {
        return lastFallbackReason.get();
    }

    public Instant lastFallbackAt() {
        return lastFallbackAt.get();
    }

    public Instant lastSuccessAt() {
        return lastSuccessAt.get();
    }
}
