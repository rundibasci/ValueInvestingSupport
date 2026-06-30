package it.mazzoni.vis.moat.dto;

import it.mazzoni.vis.domain.entity.CapitalAllocationResult;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CapitalAllocationResponse(
        String symbol,
        LocalDate resultDate,
        String sharesOutstandingTrend,
        String classification,
        Integer yearsAnalyzed,
        BigDecimal sharesChangePercentage,
        BigDecimal sharesCagr,
        BigDecimal dividendYield,
        BigDecimal netBuybackYield,
        BigDecimal totalShareholderYield,
        BigDecimal insiderOwnershipPercentage,
        BigDecimal acquisitionSpendToFcf,
        String availabilityMessage
) {
    public static CapitalAllocationResponse from(CapitalAllocationResult result) {
        return new CapitalAllocationResponse(
                result.getSecurity().getSymbol(),
                result.getResultDate(),
                result.getSharesOutstandingTrend().name(),
                result.getClassification().name(),
                result.getYearsAnalyzed(),
                result.getSharesChangePercentage(),
                result.getSharesCagr(),
                result.getDividendYield(),
                result.getNetBuybackYield(),
                result.getTotalShareholderYield(),
                result.getInsiderOwnershipPercentage(),
                result.getAcquisitionSpendToFcf(),
                result.getAvailabilityMessage()
        );
    }
}
