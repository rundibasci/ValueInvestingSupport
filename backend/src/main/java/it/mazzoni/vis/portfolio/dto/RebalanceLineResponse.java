package it.mazzoni.vis.portfolio.dto;
import java.math.BigDecimal;
public record RebalanceLineResponse(String symbol, BigDecimal capturedPrice, BigDecimal currentQuantity, BigDecimal targetQuantity, BigDecimal deltaQuantity, BigDecimal estimatedTradeValue, String side) {}
