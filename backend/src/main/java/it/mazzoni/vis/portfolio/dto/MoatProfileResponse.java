package it.mazzoni.vis.portfolio.dto;

import java.math.BigDecimal;

public record MoatProfileResponse(
        BigDecimal widePercent,
        BigDecimal narrowPercent,
        BigDecimal nonePercent,
        BigDecimal unknownPercent
) {
}
