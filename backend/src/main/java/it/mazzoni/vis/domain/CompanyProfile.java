package it.mazzoni.vis.domain;

import java.math.BigDecimal;

public record CompanyProfile(
        String symbol,
        String companyName,
        String sector,
        String industry,
        String country,
        String currency,
        String exchange,
        BigDecimal marketCap,
        String description,
        String website
) {}
