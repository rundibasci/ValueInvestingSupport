package it.mazzoni.vis.moat.dto;

import it.mazzoni.vis.domain.entity.MoatResult;
import it.mazzoni.vis.domain.entity.RoicObservation;
import it.mazzoni.vis.domain.entity.StabilityResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MoatResponse(
        String symbol,
        LocalDate resultDate,
        String moatStrength,
        String roicTrend,
        Integer yearsAnalyzed,
        Integer yearsRoicAboveWacc,
        BigDecimal roicConsistencyPercentage,
        BigDecimal averageRoic,
        BigDecimal estimatedWacc,
        BigDecimal averageRoicSpread,
        BigDecimal trendSlope,
        BigDecimal reinvestmentRate,
        String availabilityMessage,
        String methodologyDisclaimer,
        List<RoicObservationResponse> roicObservations,
        List<StabilityCriterionResponse> stabilityCriteria
) {
    public static MoatResponse from(MoatResult result, List<RoicObservation> observations, List<StabilityResult> criteria) {
        return new MoatResponse(
                result.getSecurity().getSymbol(),
                result.getResultDate(),
                result.getMoatStrength().name(),
                result.getRoicTrend().name(),
                result.getYearsAnalyzed(),
                result.getYearsRoicAboveWacc(),
                result.getRoicConsistencyPercentage(),
                result.getAverageRoic(),
                result.getEstimatedWacc(),
                result.getAverageRoicSpread(),
                result.getTrendSlope(),
                result.getReinvestmentRate(),
                result.getAvailabilityMessage(),
                "Derived ROIC is an internal estimate based on reported financial inputs and may differ from provider or company calculations. This is decision-support information, not investment advice.",
                observations.stream().map(RoicObservationResponse::from).toList(),
                criteria.stream().map(StabilityCriterionResponse::from).toList()
        );
    }
}
