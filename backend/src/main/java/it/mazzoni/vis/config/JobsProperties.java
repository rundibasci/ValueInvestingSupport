package it.mazzoni.vis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "app.jobs")
public record JobsProperties(
        boolean enabled,
        List<String> exchanges,
        Map<String, String> cron
) {
    public String cronFor(String jobKey) {
        return cron != null ? cron.getOrDefault(jobKey, "-") : "-";
    }
}
