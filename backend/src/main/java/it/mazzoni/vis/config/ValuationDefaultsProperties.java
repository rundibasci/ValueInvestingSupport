package it.mazzoni.vis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;

@ConfigurationProperties("valuation.defaults")
public record ValuationDefaultsProperties(
        BigDecimal wacc,
        BigDecimal growthY1Y5,
        BigDecimal growthY6Y10,
        BigDecimal terminalRate
) {}
