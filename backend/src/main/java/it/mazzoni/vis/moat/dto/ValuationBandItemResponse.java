package it.mazzoni.vis.moat.dto;

import it.mazzoni.vis.domain.entity.ValuationBandResult;

import java.math.BigDecimal;

public record ValuationBandItemResponse(
        String metric,
        Integer yearsAnalyzed,
        BigDecimal currentValue,
        BigDecimal medianValue,
        BigDecimal percentile25,
        BigDecimal percentile75,
        BigDecimal currentPercentile,
        String position,
        String availabilityMessage
) {
    public static ValuationBandItemResponse from(ValuationBandResult result) {
        return new ValuationBandItemResponse(
                result.getMetric(),
                result.getYearsAnalyzed(),
                result.getCurrentValue(),
                result.getMedianValue(),
                result.getPercentile25(),
                result.getPercentile75(),
                result.getCurrentPercentile(),
                result.getPosition().name(),
                result.getAvailabilityMessage()
        );
    }
}
