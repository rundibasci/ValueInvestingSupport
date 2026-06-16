package it.mazzoni.vis.marketdata.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpInsiderTradingEntry(
        String symbol,
        String transactionDate,
        String reportingName,
        String title,
        String transactionType,
        Long securitiesTransacted,
        BigDecimal price
) {}
