package it.mazzoni.vis.scoring.dto;

import it.mazzoni.vis.domain.entity.ValueScore;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ValueScoreResponse(
        String symbol,
        String companyName,
        BigDecimal totalScore,
        BigDecimal mosScore,
        BigDecimal qualityScore,
        BigDecimal safetyScore,
        BigDecimal growthScore,
        BigDecimal dividendScore,
        LocalDate scoreDate
) {
    public static ValueScoreResponse from(ValueScore score) {
        return new ValueScoreResponse(
                score.getSecurity().getSymbol(),
                score.getSecurity().getCompanyName(),
                score.getTotalScore(),
                score.getMosScore(),
                score.getQualityScore(),
                score.getSafetyScore(),
                score.getGrowthScore(),
                score.getDividendScore(),
                score.getScoreDate()
        );
    }
}
