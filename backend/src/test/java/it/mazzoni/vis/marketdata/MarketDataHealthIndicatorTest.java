package it.mazzoni.vis.marketdata;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataHealthIndicatorTest {

    @Test
    void health_reportsDegradedWhenFmpFallsBack() {
        MarketDataStatusTracker tracker = new MarketDataStatusTracker();
        tracker.recordFallback("PLAN_RESTRICTION");
        MarketDataHealthIndicator indicator = new MarketDataHealthIndicator(new MarketDataProperties("fmp"), tracker);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getDetails()).containsEntry("configuredSource", "fmp");
        assertThat(health.getDetails()).containsEntry("lastProvider", "yahoo");
    }

    @Test
    void health_reportsUpForYahooSource() {
        MarketDataStatusTracker tracker = new MarketDataStatusTracker();
        tracker.recordSuccess("yahoo");
        MarketDataHealthIndicator indicator = new MarketDataHealthIndicator(new MarketDataProperties("yahoo"), tracker);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }
}
