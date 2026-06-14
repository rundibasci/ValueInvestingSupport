package it.mazzoni.vis.marketdata.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpCashFlowEntry(
        String symbol,
        String date,
        BigDecimal operatingCashFlow,
        BigDecimal capitalExpenditure,
        BigDecimal freeCashFlow
) {}
