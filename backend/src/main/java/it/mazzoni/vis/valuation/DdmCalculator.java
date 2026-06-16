package it.mazzoni.vis.valuation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DdmCalculator {

    private static final int RESULT_SCALE = 2;
    private static final int MIN_CONSECUTIVE_DIVIDEND_YEARS = 5;

    public static BigDecimal calculate(BigDecimal dpsTtm, BigDecimal dividendGrowthRate,
                                       BigDecimal requiredReturn, int consecutiveDividendYears) {
        if (consecutiveDividendYears < MIN_CONSECUTIVE_DIVIDEND_YEARS) {
            throw new DdmNotEligibleException(
                    "DDM requires at least " + MIN_CONSECUTIVE_DIVIDEND_YEARS
                    + " consecutive dividend years; got: " + consecutiveDividendYears);
        }
        if (requiredReturn.compareTo(dividendGrowthRate) <= 0) {
            throw new DdmNotApplicableException(
                    "Required return (" + requiredReturn + ") must exceed growth rate ("
                    + dividendGrowthRate + ")");
        }
        BigDecimal denominator = requiredReturn.subtract(dividendGrowthRate);
        return dpsTtm.divide(denominator, RESULT_SCALE, RoundingMode.HALF_UP);
    }
}
