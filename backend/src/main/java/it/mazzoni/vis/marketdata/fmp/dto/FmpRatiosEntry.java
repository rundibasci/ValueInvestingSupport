package it.mazzoni.vis.marketdata.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpRatiosEntry(
        String symbol,
        String date,
        BigDecimal peRatio,
        BigDecimal priceToBookRatio,
        BigDecimal returnOnEquity,
        BigDecimal returnOnAssets,
        BigDecimal returnOnCapitalEmployed,
        BigDecimal debtToEquity,
        BigDecimal currentRatio,
        BigDecimal dividendYield,
        BigDecimal payoutRatio,
        BigDecimal netProfitMargin,
        BigDecimal operatingProfitMargin,
        BigDecimal grossProfitMargin
) {}
