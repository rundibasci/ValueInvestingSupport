package it.mazzoni.vis.admin;

import java.math.BigDecimal;

public record UniversePreviewRow(
        String symbol,
        String companyName,
        String exchange,
        String country,
        String sector,
        BigDecimal marketCap,
        Long volume
) {}
