package it.mazzoni.vis.marketdata.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpKeyMetricsEntry(
        String symbol,
        String date,
        BigDecimal returnOnEquity,
        BigDecimal returnOnAssets,
        BigDecimal returnOnInvestedCapital,
        BigDecimal returnOnCapitalEmployed
) {}
