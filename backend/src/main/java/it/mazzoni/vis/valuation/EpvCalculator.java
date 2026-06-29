package it.mazzoni.vis.valuation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public class EpvCalculator {

    public Optional<EpvResult> calculate(EpvInput input) {
        if (input.annualNetIncome() == null || input.annualNetIncome().size() < 5) {
            return Optional.empty();
        }
        if (input.wacc() == null || input.wacc().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        if (input.shares() == null || input.shares().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        BigDecimal sum = input.annualNetIncome().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal normalizedEarnings = sum.divide(
                BigDecimal.valueOf(input.annualNetIncome().size()), 2, RoundingMode.HALF_UP);
        BigDecimal netDebt = input.netDebt() != null ? input.netDebt() : BigDecimal.ZERO;
        BigDecimal fairValue = normalizedEarnings
                .divide(input.wacc(), 2, RoundingMode.HALF_UP)
                .subtract(netDebt)
                .divide(input.shares(), 2, RoundingMode.HALF_UP);

        return Optional.of(new EpvResult(fairValue, normalizedEarnings, input.annualNetIncome().size()));
    }
}
