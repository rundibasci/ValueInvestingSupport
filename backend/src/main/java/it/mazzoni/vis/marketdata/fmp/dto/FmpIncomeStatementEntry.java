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
        @JsonProperty("weightedAverageShsOutDil") Long sharesOutstandingDil
) {
    public FmpIncomeStatementEntry(String symbol, String date, BigDecimal revenue, BigDecimal netIncome,
                                   BigDecimal operatingIncome, BigDecimal grossProfit, BigDecimal eps,
                                   BigDecimal epsDiluted, Long sharesOutstandingDil) {
        this(symbol, date, revenue, netIncome, operatingIncome, null, null, grossProfit, eps, epsDiluted,
                sharesOutstandingDil);
    }
}
