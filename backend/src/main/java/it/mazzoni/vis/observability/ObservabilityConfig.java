package it.mazzoni.vis.observability;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    ObservabilitySupport observabilitySupport(io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        return new ObservabilitySupport(meterRegistry);
    }
}
