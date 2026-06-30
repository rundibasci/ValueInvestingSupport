package it.mazzoni.vis.security.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ValuationDetailResponse(
        String symbol,
        String companyName,
        BigDecimal currentPrice,
        DcfScenarios dcf,
        BigDecimal dcfTerminalValuePercentage,
        boolean dcfHighTerminalDependence,
        DcfSensitivity sensitivity,
        BigDecimal grahamNumber,
        BigDecimal ddmValue,
        EpvDetail epv,
        OwnerEarningsDetail ownerEarnings,
        BigDecimal compositeFairValue,
        BigDecimal marginOfSafety,
        BigDecimal mosLow,
        BigDecimal mosHigh,
        String recommendation,
        AnalystEstimatesItem analystEstimates,
        WaccDetail wacc,
        GrahamChecklistDetail grahamChecklist,
        LocalDate dataAsOf,
        String disclaimer
) {
    public static final String MIFID_DISCLAIMER =
            "This is a decision-support tool, not investment advice (MiFID II).";

    public record DcfSensitivity(
            List<BigDecimal> waccValues,
            List<BigDecimal> terminalRateValues,
            List<DcfSensitivityCell> cells,
            BigDecimal baseWacc,
            BigDecimal baseTerminalRate
    ) {}

    public record DcfSensitivityCell(
            BigDecimal wacc,
            BigDecimal terminalRate,
            BigDecimal fairValue,
            BigDecimal terminalValuePercentage,
            boolean highTerminalDependence
    ) {}

    public record EpvDetail(
            BigDecimal fairValue,
            BigDecimal normalizedEarnings,
            Integer yearsAveraged
    ) {}

    public record OwnerEarningsDetail(
            BigDecimal value,
            BigDecimal maintenanceCapexEstimate
    ) {}

    public record WaccDetail(
            BigDecimal wacc,
            BigDecimal riskFreeRate,
            BigDecimal equityRiskPremium,
            BigDecimal beta,
            BigDecimal costOfEquity,
            BigDecimal costOfDebt,
            BigDecimal debtWeight,
            BigDecimal equityWeight,
            BigDecimal effectiveTaxRate,
            boolean fallbackUsed,
            String source
    ) {}

    public record GrahamChecklistDetail(
            int passed,
            int failed,
            int insufficient,
            List<GrahamChecklistCriterion> criteria
    ) {}

    public record GrahamChecklistCriterion(
            String code,
            String label,
            String status,
            BigDecimal actualValue
    ) {}
}
