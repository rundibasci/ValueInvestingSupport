package it.mazzoni.vis.thesis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Mirrors vis-model-training/schemas/thesis-input.schema.json exactly (field-for-field,
 * including which fields are nullable) — the JSON contract sent to Gemini as the user
 * message. Never edited independently of that schema (TA1/TA2 reuse decision).
 */
public record ThesisInput(
        String symbol,
        String companyName,
        LocalDate analysisDate,
        BigDecimal marketPrice,
        BigDecimal intrinsicValue,
        BigDecimal marginOfSafetyPercent,
        BigDecimal valueScore,
        BigDecimal dividendYieldPercent,
        BigDecimal payoutRatioPercent,
        BigDecimal netDebtToEbitda,
        BigDecimal ffoPerShare,
        BigDecimal affoPerShare,
        BigDecimal priceToFfo,
        BigDecimal priceToAffo,
        BigDecimal affoPayoutRatio,
        Trend revenueTrend,
        Trend earningsTrend,
        Trend freeCashFlowTrend,
        DataQuality dataQuality,
        List<String> deterministicWarnings
) {}
