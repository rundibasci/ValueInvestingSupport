package it.mazzoni.vis.marketdata.fmp.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpStockListEntry(
        String symbol,
        @JsonAlias("companyName") String name,
        String country,
        String sector,
        String exchange,
        String exchangeShortName,
        String type,
        BigDecimal price,
        BigDecimal marketCap,
        Long volume,
        Boolean isEtf,
        Boolean isFund
) {}
