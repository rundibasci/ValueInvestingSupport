package it.mazzoni.vis.marketdata.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpStockListEntry(
        String symbol,
        String name,
        String exchange,
        String exchangeShortName,
        String type
) {}
