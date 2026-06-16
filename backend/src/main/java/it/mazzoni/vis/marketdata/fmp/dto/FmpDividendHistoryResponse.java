package it.mazzoni.vis.marketdata.fmp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpDividendHistoryResponse(
        String symbol,
        List<FmpDividendEntry> historical
) {}
