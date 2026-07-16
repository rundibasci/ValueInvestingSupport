package it.mazzoni.vis.portfolio.importing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.portfolio-import")
public record PortfolioImportProperties(long maxUploadBytes, int maxRows, int previewRetentionHours,
                                        String defaultBaseCurrency) {
    public PortfolioImportProperties {
        if (maxUploadBytes <= 0) maxUploadBytes = 1_048_576;
        if (maxRows <= 0) maxRows = 1_000;
        if (previewRetentionHours <= 0) previewRetentionHours = 24;
        if (defaultBaseCurrency == null || defaultBaseCurrency.isBlank()) defaultBaseCurrency = "EUR";
    }
}
