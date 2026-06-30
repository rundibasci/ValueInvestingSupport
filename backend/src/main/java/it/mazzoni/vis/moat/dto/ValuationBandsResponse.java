package it.mazzoni.vis.moat.dto;

import it.mazzoni.vis.domain.entity.ValuationBandResult;

import java.time.LocalDate;
import java.util.List;

public record ValuationBandsResponse(
        String symbol,
        LocalDate resultDate,
        List<ValuationBandItemResponse> bands
) {
    public static ValuationBandsResponse from(String symbol, List<ValuationBandResult> bands) {
        LocalDate resultDate = bands.stream().map(ValuationBandResult::getResultDate).findFirst().orElse(null);
        return new ValuationBandsResponse(symbol, resultDate, bands.stream().map(ValuationBandItemResponse::from).toList());
    }
}
