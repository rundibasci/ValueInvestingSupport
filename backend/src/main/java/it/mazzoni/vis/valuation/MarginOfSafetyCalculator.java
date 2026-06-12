package it.mazzoni.vis.valuation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MarginOfSafetyCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int RESULT_SCALE = 2;

    public static BigDecimal compute(BigDecimal fairValue, BigDecimal currentPrice) {
        if (fairValue == null || currentPrice == null
                || fairValue.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return fairValue.subtract(currentPrice)
                .divide(fairValue, RESULT_SCALE + 4, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }
}
