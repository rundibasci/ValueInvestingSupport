package it.mazzoni.vis.marketdata.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpProfileEntry(
        String symbol,
        String companyName,
        String sector,
        String industry,
        String country,
        String currency,
        String exchangeShortName,
        BigDecimal mktCap,
        BigDecimal price
) {}
