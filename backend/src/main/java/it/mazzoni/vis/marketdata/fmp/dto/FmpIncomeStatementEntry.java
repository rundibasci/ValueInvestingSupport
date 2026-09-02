package it.mazzoni.vis.marketdata.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpIncomeStatementEntry(
        String symbol,
        String date,
        BigDecimal revenue,
        BigDecimal netIncome,
        BigDecimal operatingIncome,
        BigDecimal incomeBeforeTax,
        BigDecimal incomeTaxExpense,
        BigDecimal grossProfit,
        BigDecimal eps,
        @JsonProperty("epsDiluted") BigDecimal epsDiluted,
        @JsonProperty("weightedAverageShsOutDil") Long sharesOutstandingDil,
        // RM1 (specs/sector-aware-valuation-metrics.md): confirmed present on FMP Premium's
        // /income-statement for REIT symbols (verified live against O, PLD, SPG) — both are FFO
        // inputs (D&A add-back) and, for ebitda, feed Debt/EBITDA and interest-coverage safety
        // metrics directly rather than the operating-income approximation used elsewhere.
        BigDecimal depreciationAndAmortization,
        BigDecimal ebitda,
        // RM2 (specs/sector-aware-valuation-metrics.md): confirmed present and populated on the
        // same live /income-statement payload (re-verified against O, PLD, SPG during RM2, not
        // assumed from RM1's field list) — feeds EBITDA interest coverage in the Safety pillar.
        BigDecimal interestExpense
) {
    public FmpIncomeStatementEntry(String symbol, String date, BigDecimal revenue, BigDecimal netIncome,
                                   BigDecimal operatingIncome, BigDecimal grossProfit, BigDecimal eps,
                                   BigDecimal epsDiluted, Long sharesOutstandingDil) {
        this(symbol, date, revenue, netIncome, operatingIncome, null, null, grossProfit, eps, epsDiluted,
                sharesOutstandingDil, null, null, null);
    }
}
