package it.mazzoni.vis.valuation;

import java.math.BigDecimal;

public record DcfResult(
        BigDecimal fairValue,
        BigDecimal fairValueLow,
        BigDecimal fairValueHigh,
        BigDecimal enterpriseValue,
        DcfInput parameters
) {}
