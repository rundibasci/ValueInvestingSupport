package it.mazzoni.vis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * RM2 ({@code specs/sector-aware-valuation-metrics.md} §7, §10 open question 3) — REIT pillar
 * calibration thresholds for {@link it.mazzoni.vis.scoring.ValueScoreService}'s REIT-branch Quality
 * (FFO margin), Safety (Net Debt/EBITDA + EBITDA interest coverage), and Dividend (AFFO payout
 * ratio) sub-scores.
 *
 * <p><b>These are first-pass calibrations</b>, not sourced from a specific published NAREIT/sector
 * benchmark dataset — the source doc explicitly defers exact threshold sourcing to this phase
 * ("propose sourcing from public NAREIT/sector benchmark data during RM2, not guessing here"),
 * interpreted as "make an initial, documented, config-driven call" rather than as a blocker with no
 * defined data source or owner. Mirrors {@link ValuationDefaultsProperties}/{@link ScoringRiskProperties}'s
 * existing {@code @ConfigurationProperties} record pattern so recalibration against real data later
 * is a config change, not a code change.
 */
@ConfigurationProperties(prefix = "scoring.sector-metric.reit")
public record SectorMetricProperties(
        BigDecimal qualityFfoMarginHigh,
        BigDecimal qualityFfoMarginMid,
        BigDecimal qualityFfoMarginLow,
        BigDecimal safetyNetDebtEbitdaConservative,
        BigDecimal safetyNetDebtEbitdaModerate,
        BigDecimal safetyNetDebtEbitdaHigh,
        BigDecimal safetyInterestCoverageBonusThreshold,
        BigDecimal dividendAffoPayoutConservative,
        BigDecimal dividendAffoPayoutModerate,
        BigDecimal dividendAffoPayoutHigh
) {
    public SectorMetricProperties {
        qualityFfoMarginHigh = qualityFfoMarginHigh != null ? qualityFfoMarginHigh : new BigDecimal("0.50");
        qualityFfoMarginMid = qualityFfoMarginMid != null ? qualityFfoMarginMid : new BigDecimal("0.35");
        qualityFfoMarginLow = qualityFfoMarginLow != null ? qualityFfoMarginLow : new BigDecimal("0.20");
        safetyNetDebtEbitdaConservative = safetyNetDebtEbitdaConservative != null
                ? safetyNetDebtEbitdaConservative : new BigDecimal("5.5");
        safetyNetDebtEbitdaModerate = safetyNetDebtEbitdaModerate != null
                ? safetyNetDebtEbitdaModerate : new BigDecimal("6.5");
        safetyNetDebtEbitdaHigh = safetyNetDebtEbitdaHigh != null ? safetyNetDebtEbitdaHigh : new BigDecimal("8.0");
        safetyInterestCoverageBonusThreshold = safetyInterestCoverageBonusThreshold != null
                ? safetyInterestCoverageBonusThreshold : new BigDecimal("3.0");
        dividendAffoPayoutConservative = dividendAffoPayoutConservative != null
                ? dividendAffoPayoutConservative : new BigDecimal("0.85");
        dividendAffoPayoutModerate = dividendAffoPayoutModerate != null
                ? dividendAffoPayoutModerate : new BigDecimal("1.00");
        dividendAffoPayoutHigh = dividendAffoPayoutHigh != null ? dividendAffoPayoutHigh : new BigDecimal("1.15");
    }
}
