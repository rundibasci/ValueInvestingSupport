package it.mazzoni.vis.scoring.dto;

import it.mazzoni.vis.domain.entity.EarningsQualityResult;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EarningsQualityResponse(
        String symbol,
        BigDecimal fcfToNetIncome,
        BigDecimal sloanAccrualsRatio,
        String classification,
        boolean deteriorating,
        int yearsAnalyzed,
        LocalDate resultDate,
        String availabilityStatus,
        String availabilityMessage
) {
    public static EarningsQualityResponse from(EarningsQualityResult result) {
        return new EarningsQualityResponse(
                result.getSecurity().getSymbol(),
                result.getFcfToNetIncome(),
                result.getSloanAccrualsRatio(),
                result.getClassification().name(),
                result.isDeteriorating(),
                result.getYearsAnalyzed(),
                result.getResultDate(),
                result.getAvailabilityStatus().name(),
                result.getAvailabilityMessage()
        );
    }
}
