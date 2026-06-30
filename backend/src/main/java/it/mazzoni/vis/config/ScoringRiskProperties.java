package it.mazzoni.vis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

@ConfigurationProperties(prefix = "scoring.risk")
public record ScoringRiskProperties(
        Map<String, WeightProfile> weightProfiles
) {
    public WeightProfile profile(String key) {
        if (weightProfiles == null) {
            return defaultProfile(key);
        }
        return weightProfiles.getOrDefault(key, defaultProfile(key));
    }

    private static WeightProfile defaultProfile(String key) {
        return switch (key) {
            case "non-dividend-growth" -> new WeightProfile(new BigDecimal("30"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("20"), BigDecimal.ZERO);
            case "reit-utility" -> new WeightProfile(new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("10"), new BigDecimal("10"));
            case "financial" -> new WeightProfile(new BigDecimal("30"), new BigDecimal("25"), new BigDecimal("25"), new BigDecimal("10"), new BigDecimal("10"));
            case "cyclical" -> new WeightProfile(new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("25"), new BigDecimal("15"), new BigDecimal("10"));
            default -> new WeightProfile(new BigDecimal("30"), new BigDecimal("25"), new BigDecimal("20"), new BigDecimal("15"), new BigDecimal("10"));
        };
    }

    public record WeightProfile(
            BigDecimal mos,
            BigDecimal quality,
            BigDecimal safety,
            BigDecimal growth,
            BigDecimal dividend
    ) {}
}
