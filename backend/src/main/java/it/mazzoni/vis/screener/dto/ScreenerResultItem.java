package it.mazzoni.vis.screener.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScreenerResultItem(
        String symbol,
        String companyName,
        String sector,
        String exchange,
        BigDecimal currentPrice,
        BigDecimal compositeFairValue,
        BigDecimal marginOfSafety,
        BigDecimal totalScore,
        BigDecimal mosScore,
        BigDecimal qualityScore,
        BigDecimal safetyScore,
        BigDecimal growthScore,
        BigDecimal dividendScore,
        String recommendation,
        LocalDate scoreDate
) {}
