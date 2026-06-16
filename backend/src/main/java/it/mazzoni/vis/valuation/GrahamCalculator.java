package it.mazzoni.vis.valuation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class GrahamCalculator {

    private static final BigDecimal MULTIPLIER = new BigDecimal("22.5");
    private static final MathContext SQRT_CONTEXT = new MathContext(10, RoundingMode.HALF_UP);
    private static final int RESULT_SCALE = 2;

    public static BigDecimal calculate(BigDecimal eps, BigDecimal bvps) {
        if (eps == null || eps.compareTo(BigDecimal.ZERO) <= 0) {
            throw new GrahamNotApplicableException("EPS must be positive; got: " + eps);
        }
        if (bvps == null || bvps.compareTo(BigDecimal.ZERO) <= 0) {
            throw new GrahamNotApplicableException("BVPS must be positive; got: " + bvps);
        }
        BigDecimal product = MULTIPLIER.multiply(eps).multiply(bvps);
        return product.sqrt(SQRT_CONTEXT).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }
}
