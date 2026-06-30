package it.mazzoni.vis.scoring.dto;

import it.mazzoni.vis.domain.entity.AltmanResult;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AltmanResponse(
        String symbol,
        BigDecimal score,
        String zone,
        String formulaVariant,
        BigDecimal workingCapitalToAssets,
        BigDecimal retainedEarningsToAssets,
        BigDecimal ebitToAssets,
        BigDecimal marketValueEquityToLiabilities,
        BigDecimal salesToAssets,
        LocalDate resultDate,
        String availabilityStatus,
        String availabilityMessage
) {
    public static AltmanResponse from(AltmanResult result) {
        return new AltmanResponse(
                result.getSecurity().getSymbol(),
                result.getScore(),
                result.getZone().name(),
                result.getFormulaVariant().name(),
                result.getWorkingCapitalToAssets(),
                result.getRetainedEarningsToAssets(),
                result.getEbitToAssets(),
                result.getMarketValueEquityToLiabilities(),
                result.getSalesToAssets(),
                result.getResultDate(),
                result.getAvailabilityStatus().name(),
                result.getAvailabilityMessage()
        );
    }
}
