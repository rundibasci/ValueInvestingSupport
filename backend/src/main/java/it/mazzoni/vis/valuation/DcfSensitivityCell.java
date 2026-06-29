package it.mazzoni.vis.valuation;

import java.math.BigDecimal;

public record DcfSensitivityCell(
        BigDecimal wacc,
        BigDecimal terminalRate,
        BigDecimal fairValue,
        BigDecimal terminalValuePercentage,
        boolean highTerminalDependence
) {}
