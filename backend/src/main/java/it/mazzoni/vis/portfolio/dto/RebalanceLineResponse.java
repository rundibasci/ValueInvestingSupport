package it.mazzoni.vis.portfolio.dto;
import java.math.BigDecimal;
public record RebalanceLineResponse(
        String symbol,
        BigDecimal capturedPrice,
        BigDecimal currentQuantity,
        BigDecimal targetQuantity,
        BigDecimal deltaQuantity,
        BigDecimal estimatedTradeValue,
        String side,
        String urgency,
        BigDecimal estimatedTransactionCost,
        String holdingPeriod,
        String positionSizeWarning
) {
    public RebalanceLineResponse(String symbol, BigDecimal capturedPrice, BigDecimal currentQuantity,
                                 BigDecimal targetQuantity, BigDecimal deltaQuantity,
                                 BigDecimal estimatedTradeValue, String side) {
        this(symbol, capturedPrice, currentQuantity, targetQuantity, deltaQuantity, estimatedTradeValue, side,
                "HOLD", BigDecimal.ZERO, "UNKNOWN", null);
    }
}
