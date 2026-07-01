package it.mazzoni.vis.portfolio.dto;

import java.math.BigDecimal;

public record HoldingConcentrationResponse(String symbol, BigDecimal weightPercent, String status) {
}
