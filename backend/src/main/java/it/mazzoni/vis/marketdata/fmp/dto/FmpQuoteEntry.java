package it.mazzoni.vis.marketdata.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpQuoteEntry(
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changesPercentage
) {}
