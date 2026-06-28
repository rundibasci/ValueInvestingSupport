package it.mazzoni.vis.marketdata;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component("marketData")
public class MarketDataHealthIndicator implements HealthIndicator {

    private final MarketDataProperties properties;
    private final MarketDataStatusTracker statusTracker;

    public MarketDataHealthIndicator(MarketDataProperties properties,
                                     MarketDataStatusTracker statusTracker) {
        this.properties = properties;
        this.statusTracker = statusTracker;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("configuredSource", properties.source());
        details.put("lastProvider", statusTracker.lastProvider());
        details.put("lastSuccessAt", statusTracker.lastSuccessAt());
        details.put("lastFallbackAt", statusTracker.lastFallbackAt());
        details.put("lastFallbackReason", statusTracker.lastFallbackReason());

        boolean degraded = "fmp".equalsIgnoreCase(properties.source())
                && statusTracker.lastFallbackAt() != null;
        return degraded
                ? Health.status("DEGRADED").withDetails(details).build()
                : Health.up().withDetails(details).build();
    }
}
