package it.mazzoni.vis.valuation;

import java.math.BigDecimal;

public record DcfInput(
        BigDecimal fcfTtm,
        BigDecimal growthY1Y5,
        BigDecimal growthY6Y10,
        BigDecimal terminalRate,
        BigDecimal wacc,
        BigDecimal shares,
        BigDecimal netDebt,
        int fcfYearsPositive
) {}
