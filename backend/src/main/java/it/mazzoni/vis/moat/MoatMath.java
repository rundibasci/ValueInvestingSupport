package it.mazzoni.vis.moat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

final class MoatMath {
    private MoatMath() {}

    static BigDecimal normalizeRatio(BigDecimal value) {
        if (value == null) return null;
        return value.compareTo(BigDecimal.ONE) > 0
                ? value.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                : value;
    }

    static BigDecimal pct(BigDecimal decimal) {
        return decimal != null ? decimal.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) : null;
    }

    static BigDecimal avg(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(v -> v != null).toList();
        if (present.isEmpty()) return null;
        return present.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(present.size()), 6, RoundingMode.HALF_UP);
    }

    static BigDecimal percentChange(BigDecimal latest, BigDecimal oldest) {
        if (latest == null || oldest == null || oldest.compareTo(BigDecimal.ZERO) == 0) return null;
        return latest.subtract(oldest).divide(oldest, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal cagr(BigDecimal latest, BigDecimal oldest, int years) {
        if (latest == null || oldest == null || years <= 0 || latest.compareTo(BigDecimal.ZERO) <= 0 || oldest.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        double result = Math.pow(latest.divide(oldest, 10, RoundingMode.HALF_UP).doubleValue(), 1.0d / years) - 1.0d;
        return BigDecimal.valueOf(result).setScale(6, RoundingMode.HALF_UP);
    }

    static BigDecimal slope(List<BigDecimal> chronologicalValues) {
        int n = chronologicalValues.size();
        if (n < 2) return null;
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;
        for (int i = 0; i < n; i++) {
            double x = i + 1;
            double y = chronologicalValues.get(i).doubleValue();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denominator = (n * sumXX) - (sumX * sumX);
        if (denominator == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(((n * sumXY) - (sumX * sumY)) / denominator).setScale(6, RoundingMode.HALF_UP);
    }
}
