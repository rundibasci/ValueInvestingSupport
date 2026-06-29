package it.mazzoni.vis.valuation;

import java.math.BigDecimal;
import java.util.List;

public record DcfSensitivityResult(
        List<BigDecimal> waccValues,
        List<BigDecimal> terminalRateValues,
        List<DcfSensitivityCell> cells
) {}
