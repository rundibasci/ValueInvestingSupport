package it.mazzoni.vis.scoring.dto;

import it.mazzoni.vis.domain.entity.PiotroskiResult;

import java.time.LocalDate;
import java.util.Map;

public record PiotroskiResponse(
        String symbol,
        int totalScore,
        Map<String, Boolean> factors,
        LocalDate resultDate,
        String availabilityStatus,
        String availabilityMessage
) {
    public static PiotroskiResponse from(PiotroskiResult result) {
        return new PiotroskiResponse(
                result.getSecurity().getSymbol(),
                result.getTotalScore(),
                Map.of(
                        "positiveNetIncome", result.isPositiveNetIncome(),
                        "positiveOperatingCashFlow", result.isPositiveOperatingCashFlow(),
                        "improvingRoa", result.isImprovingRoa(),
                        "cashFlowQuality", result.isCashFlowQuality(),
                        "lowerLeverage", result.isLowerLeverage(),
                        "improvingCurrentRatio", result.isImprovingCurrentRatio(),
                        "noShareDilution", result.isNoShareDilution(),
                        "improvingGrossMargin", result.isImprovingGrossMargin(),
                        "improvingAssetTurnover", result.isImprovingAssetTurnover()
                ),
                result.getResultDate(),
                result.getAvailabilityStatus().name(),
                result.getAvailabilityMessage()
        );
    }
}
