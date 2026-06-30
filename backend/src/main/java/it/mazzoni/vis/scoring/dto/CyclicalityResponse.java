package it.mazzoni.vis.scoring.dto;

import it.mazzoni.vis.domain.entity.CyclicalityResult;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CyclicalityResponse(
        String symbol,
        String classification,
        BigDecimal revenueCoefficient,
        BigDecimal earningsCoefficient,
        BigDecimal normalizedEarnings,
        BigDecimal cycleAdjustedPe,
        int yearsAnalyzed,
        LocalDate resultDate,
        String availabilityStatus,
        String availabilityMessage
) {
    public static CyclicalityResponse from(CyclicalityResult result) {
        return new CyclicalityResponse(
                result.getSecurity().getSymbol(),
                result.getClassification().name(),
                result.getRevenueCoefficient(),
                result.getEarningsCoefficient(),
                result.getNormalizedEarnings(),
                result.getCycleAdjustedPe(),
                result.getYearsAnalyzed(),
                result.getResultDate(),
                result.getAvailabilityStatus().name(),
                result.getAvailabilityMessage()
        );
    }
}
