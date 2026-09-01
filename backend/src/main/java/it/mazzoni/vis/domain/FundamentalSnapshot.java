package it.mazzoni.vis.domain;

import java.math.BigDecimal;
import java.util.List;

public record FundamentalSnapshot(
        String symbol,
        String companyName,
        String sector,
        String industry,
        String country,
        String currency,
        BigDecimal currentPrice,
        BigDecimal epsTtm,
        BigDecimal bookValuePerShare,
        Long sharesOutstanding,
        List<BigDecimal> revenueHistory,
        List<BigDecimal> netIncomeHistory,
        List<BigDecimal> fcfHistory,
        List<BigDecimal> epsHistory,
        List<Long> sharesOutstandingHistory,
        List<BigDecimal> operatingIncomeHistory,
        List<BigDecimal> operatingCashFlowHistory,
        List<BigDecimal> totalAssetsHistory,
        List<BigDecimal> totalLiabilitiesHistory,
        List<BigDecimal> totalDebtHistory,
        List<BigDecimal> cashHistory,
        List<BigDecimal> totalEquityHistory,
        List<BigDecimal> pretaxIncomeHistory,
        List<BigDecimal> incomeTaxExpenseHistory,
        // RM1 (specs/sector-aware-valuation-metrics.md): FFO/Debt-EBITDA inputs — empty for
        // Yahoo fallback (Design Principle 5: same domain type, no equivalent Yahoo field).
        List<BigDecimal> depreciationAndAmortizationHistory,
        List<BigDecimal> ebitdaHistory,
        BigDecimal netDebt,
        BigDecimal totalDebt,
        BigDecimal cash
) {
    public FundamentalSnapshot(String symbol,
                               String companyName,
                               String sector,
                               String industry,
                               String country,
                               String currency,
                               BigDecimal currentPrice,
                               BigDecimal epsTtm,
                               BigDecimal bookValuePerShare,
                               Long sharesOutstanding,
                               List<BigDecimal> revenueHistory,
                               List<BigDecimal> netIncomeHistory,
                               List<BigDecimal> fcfHistory,
                               BigDecimal netDebt,
                               BigDecimal totalDebt,
                               BigDecimal cash) {
        this(symbol, companyName, sector, industry, country, currency, currentPrice,
                epsTtm, bookValuePerShare, sharesOutstanding, revenueHistory,
                netIncomeHistory, fcfHistory, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), netDebt, totalDebt, cash);
    }

    public FundamentalSnapshot(String symbol,
                               String companyName,
                               String sector,
                               String industry,
                               String country,
                               String currency,
                               BigDecimal currentPrice,
                               BigDecimal epsTtm,
                               BigDecimal bookValuePerShare,
                               Long sharesOutstanding,
                               List<BigDecimal> revenueHistory,
                               List<BigDecimal> netIncomeHistory,
                               List<BigDecimal> fcfHistory,
                               List<BigDecimal> epsHistory,
                               List<Long> sharesOutstandingHistory,
                               BigDecimal netDebt,
                               BigDecimal totalDebt,
                               BigDecimal cash) {
        this(symbol, companyName, sector, industry, country, currency, currentPrice,
                epsTtm, bookValuePerShare, sharesOutstanding, revenueHistory,
                netIncomeHistory, fcfHistory, epsHistory, sharesOutstandingHistory,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), netDebt, totalDebt, cash);
    }

    public FundamentalSnapshot(String symbol, String companyName, String sector, String industry,
                               String country, String currency, BigDecimal currentPrice, BigDecimal epsTtm,
                               BigDecimal bookValuePerShare, Long sharesOutstanding,
                               List<BigDecimal> revenueHistory, List<BigDecimal> netIncomeHistory,
                               List<BigDecimal> fcfHistory, List<BigDecimal> epsHistory,
                               List<Long> sharesOutstandingHistory, List<BigDecimal> operatingIncomeHistory,
                               List<BigDecimal> operatingCashFlowHistory, List<BigDecimal> totalAssetsHistory,
                               List<BigDecimal> totalLiabilitiesHistory, List<BigDecimal> totalDebtHistory,
                               List<BigDecimal> cashHistory, List<BigDecimal> totalEquityHistory,
                               BigDecimal netDebt, BigDecimal totalDebt, BigDecimal cash) {
        this(symbol, companyName, sector, industry, country, currency, currentPrice, epsTtm,
                bookValuePerShare, sharesOutstanding, revenueHistory, netIncomeHistory, fcfHistory,
                epsHistory, sharesOutstandingHistory, operatingIncomeHistory, operatingCashFlowHistory,
                totalAssetsHistory, totalLiabilitiesHistory, totalDebtHistory, cashHistory, totalEquityHistory,
                List.of(), List.of(), List.of(), List.of(), netDebt, totalDebt, cash);
    }
}
