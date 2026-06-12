package it.mazzoni.vis.valuation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public class DcfCalculator {

    private static final int INTERNAL_SCALE = 10;
    private static final int RESULT_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public Optional<DcfResult> calculate(DcfInput input) {
        if (input.fcfYearsPositive() < 3) {
            return Optional.empty();
        }

        BigDecimal enterpriseValue = presentValue(
                input.fcfTtm(), input.growthY1Y5(), input.growthY6Y10(),
                input.terminalRate(), input.wacc());
        BigDecimal fairValue = toPerShare(enterpriseValue, input.netDebt(), input.shares());

        BigDecimal waccLow = input.wacc().add(new BigDecimal("0.02"));
        BigDecimal fairValueLow = toPerShare(
                presentValue(input.fcfTtm(), input.growthY1Y5(), input.growthY6Y10(),
                        input.terminalRate(), waccLow),
                input.netDebt(), input.shares());

        BigDecimal waccHigh = input.wacc().subtract(new BigDecimal("0.01"));
        BigDecimal fairValueHigh = toPerShare(
                presentValue(input.fcfTtm(), input.growthY1Y5(), input.growthY6Y10(),
                        input.terminalRate(), waccHigh),
                input.netDebt(), input.shares());

        return Optional.of(new DcfResult(fairValue, fairValueLow, fairValueHigh, enterpriseValue, input));
    }

    private BigDecimal presentValue(BigDecimal fcfTtm, BigDecimal growthY1Y5, BigDecimal growthY6Y10,
                                     BigDecimal terminalRate, BigDecimal wacc) {
        BigDecimal pv = BigDecimal.ZERO;
        BigDecimal fcf = fcfTtm;
        BigDecimal discountFactor = BigDecimal.ONE;
        BigDecimal oneWacc = BigDecimal.ONE.add(wacc);

        for (int year = 1; year <= 5; year++) {
            fcf = fcf.multiply(BigDecimal.ONE.add(growthY1Y5)).setScale(INTERNAL_SCALE, ROUNDING);
            discountFactor = discountFactor.multiply(oneWacc).setScale(INTERNAL_SCALE, ROUNDING);
            pv = pv.add(fcf.divide(discountFactor, INTERNAL_SCALE, ROUNDING));
        }

        for (int year = 6; year <= 10; year++) {
            fcf = fcf.multiply(BigDecimal.ONE.add(growthY6Y10)).setScale(INTERNAL_SCALE, ROUNDING);
            discountFactor = discountFactor.multiply(oneWacc).setScale(INTERNAL_SCALE, ROUNDING);
            pv = pv.add(fcf.divide(discountFactor, INTERNAL_SCALE, ROUNDING));
        }

        BigDecimal terminalFcf = fcf.multiply(BigDecimal.ONE.add(terminalRate))
                .setScale(INTERNAL_SCALE, ROUNDING);
        BigDecimal denominator = wacc.subtract(terminalRate);
        BigDecimal terminalValue = terminalFcf.divide(denominator, INTERNAL_SCALE, ROUNDING);
        pv = pv.add(terminalValue.divide(discountFactor, INTERNAL_SCALE, ROUNDING));

        return pv.setScale(RESULT_SCALE, ROUNDING);
    }

    private BigDecimal toPerShare(BigDecimal enterpriseValue, BigDecimal netDebt, BigDecimal shares) {
        return enterpriseValue.subtract(netDebt).divide(shares, RESULT_SCALE, ROUNDING);
    }
}
