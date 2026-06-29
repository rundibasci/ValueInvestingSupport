package it.mazzoni.vis.valuation;

import java.math.BigDecimal;
import java.util.List;

public record EpvInput(
        List<BigDecimal> annualNetIncome,
        BigDecimal wacc,
        BigDecimal netDebt,
        BigDecimal shares
) {}
