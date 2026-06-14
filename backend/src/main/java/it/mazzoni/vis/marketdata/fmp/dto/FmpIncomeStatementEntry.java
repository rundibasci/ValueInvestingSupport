package it.mazzoni.vis.marketdata.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpIncomeStatementEntry(
        String symbol,
        String date,
        BigDecimal revenue,
        BigDecimal netIncome,
        BigDecimal operatingIncome,
        BigDecimal grossProfit,
        BigDecimal eps,
        BigDecimal epsdiluted
) {}
