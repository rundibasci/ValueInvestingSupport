package it.mazzoni.vis.portfolio.dto;

import java.math.BigDecimal;
import java.util.Map;

public record QualityDistributionResponse(
        BigDecimal averageRoic,
        BigDecimal averageRoe,
        Map<String, BigDecimal> earningsQualityPercent
) {
}
