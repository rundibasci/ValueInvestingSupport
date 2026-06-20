package it.mazzoni.vis.security.dto;

import java.math.BigDecimal;

public record DcfScenarios(
        BigDecimal base,
        BigDecimal low,
        BigDecimal high
) {}
