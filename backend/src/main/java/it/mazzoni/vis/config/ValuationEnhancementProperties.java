package it.mazzoni.vis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "valuation.enhancements")
public record ValuationEnhancementProperties(
        BigDecimal riskFreeRate,
        BigDecimal equityRiskPremium,
        BigDecimal sectorFallbackWacc,
        BigDecimal maintenanceCapexDepreciationRatio,
        BigDecimal highTerminalDependenceThreshold,
        BigDecimal reducedDcfWeight
) {
    public ValuationEnhancementProperties {
        riskFreeRate = riskFreeRate != null ? riskFreeRate : new BigDecimal("0.045");
        equityRiskPremium = equityRiskPremium != null ? equityRiskPremium : new BigDecimal("0.055");
        sectorFallbackWacc = sectorFallbackWacc != null ? sectorFallbackWacc : new BigDecimal("0.09");
        maintenanceCapexDepreciationRatio = maintenanceCapexDepreciationRatio != null
                ? maintenanceCapexDepreciationRatio : new BigDecimal("0.70");
        highTerminalDependenceThreshold = highTerminalDependenceThreshold != null
                ? highTerminalDependenceThreshold : new BigDecimal("70.00");
        reducedDcfWeight = reducedDcfWeight != null ? reducedDcfWeight : new BigDecimal("0.40");
    }
}
