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
        if (input.terminalRate().compareTo(input.wacc()) >= 0) {
            throw new IllegalArgumentException(
                    "terminalRate (" + input.terminalRate() + ") must be less than wacc (" + input.wacc() + ")");
        }

        DcfPresentValue enterpriseValue = presentValue(
                input.fcfTtm(), input.growthY1Y5(), input.growthY6Y10(),
                input.terminalRate(), input.wacc());
        BigDecimal fairValue = toPerShare(enterpriseValue.totalValue(), input.netDebt(), input.shares());

        BigDecimal waccLow = input.wacc().add(new BigDecimal("0.02"));
        BigDecimal fairValueLow = toPerShare(
                presentValue(input.fcfTtm(), input.growthY1Y5(), input.growthY6Y10(),
                        input.terminalRate(), waccLow).totalValue(),
                input.netDebt(), input.shares());

        BigDecimal waccHigh = input.wacc().subtract(new BigDecimal("0.01"));
        BigDecimal fairValueHigh = toPerShare(
                presentValue(input.fcfTtm(), input.growthY1Y5(), input.growthY6Y10(),
                        input.terminalRate(), waccHigh).totalValue(),
                input.netDebt(), input.shares());

        BigDecimal terminalPercentage = enterpriseValue.terminalValue()
                .divide(enterpriseValue.totalValue(), 6, ROUNDING)
                .multiply(new BigDecimal("100"))
                .setScale(2, ROUNDING);
        boolean highTerminalDependence = terminalPercentage.compareTo(new BigDecimal("70.00")) > 0;

        return Optional.of(new DcfResult(
                fairValue,
                fairValueLow,
                fairValueHigh,
                enterpriseValue.totalValue(),
                terminalPercentage,
                highTerminalDependence,
                input));
    }

    private DcfPresentValue presentValue(BigDecimal fcfTtm, BigDecimal growthY1Y5, BigDecimal growthY6Y10,
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
        BigDecimal discountedTerminalValue = terminalValue.divide(discountFactor, INTERNAL_SCALE, ROUNDING);
        pv = pv.add(discountedTerminalValue);

        return new DcfPresentValue(
                pv.setScale(RESULT_SCALE, ROUNDING),
                discountedTerminalValue.setScale(RESULT_SCALE, ROUNDING));
    }

    private BigDecimal toPerShare(BigDecimal enterpriseValue, BigDecimal netDebt, BigDecimal shares) {
        return enterpriseValue.subtract(netDebt).divide(shares, RESULT_SCALE, ROUNDING);
    }

    private record DcfPresentValue(BigDecimal totalValue, BigDecimal terminalValue) {}
}
