package it.mazzoni.vis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;

@ConfigurationProperties(prefix = "valuation.weights")
public record ValuationWeightsProperties(
        BigDecimal dcf,
        BigDecimal graham,
        BigDecimal ddm
) {}
