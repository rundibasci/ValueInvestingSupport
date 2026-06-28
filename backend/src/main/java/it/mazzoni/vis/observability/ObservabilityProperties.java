package it.mazzoni.vis.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.observability")
public class ObservabilityProperties {

    private boolean requestLoggingEnabled = true;
    private String correlationHeader = "X-Correlation-ID";

    public ObservabilityProperties() {
    }

    public ObservabilityProperties(boolean requestLoggingEnabled, String correlationHeader) {
        this.requestLoggingEnabled = requestLoggingEnabled;
        setCorrelationHeader(correlationHeader);
    }

    public boolean requestLoggingEnabled() {
        return requestLoggingEnabled;
    }

    public void setRequestLoggingEnabled(boolean requestLoggingEnabled) {
        this.requestLoggingEnabled = requestLoggingEnabled;
    }

    public String correlationHeader() {
        return correlationHeader;
    }

    public void setCorrelationHeader(String correlationHeader) {
        if (correlationHeader != null && !correlationHeader.isBlank()) {
            this.correlationHeader = correlationHeader;
        }
    }
}
