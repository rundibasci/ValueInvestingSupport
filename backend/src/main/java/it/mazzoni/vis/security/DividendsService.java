package it.mazzoni.vis.security;

import it.mazzoni.vis.domain.entity.DividendRecord;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class DividendsService {

    public int computeStreak(List<DividendRecord> records) {
        if (records.isEmpty()) return 0;

        int latestYear = records.stream()
                .mapToInt(r -> r.getExDividendDate().getYear())
                .max()
                .orElse(0);

        Map<Integer, Boolean> yearHasDividend = records.stream()
                .collect(Collectors.toMap(
                        r -> r.getExDividendDate().getYear(),
                        r -> true,
                        (a, b) -> true));

        int streak = 0;
        for (int year = latestYear; yearHasDividend.getOrDefault(year, false); year--) {
            streak++;
        }
        return streak;
    }

    public BigDecimal computeCagr(List<DividendRecord> records, int years) {
        if (records.isEmpty()) return null;

        TreeMap<Integer, BigDecimal> annualDps = new TreeMap<>();
        for (DividendRecord r : records) {
            int year = r.getExDividendDate().getYear();
            annualDps.merge(year, r.getAmount(), BigDecimal::add);
        }

        if (annualDps.size() < years + 1) return null;

        List<Integer> sortedYears = annualDps.navigableKeySet().descendingSet()
                .stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());

        int latestYear = sortedYears.get(0);
        int baseYear = latestYear - years;

        BigDecimal latestDps = annualDps.get(latestYear);
        BigDecimal baseDps = annualDps.get(baseYear);

        if (latestDps == null || baseDps == null || baseDps.compareTo(BigDecimal.ZERO) == 0) return null;

        double cagr = (Math.pow(latestDps.doubleValue() / baseDps.doubleValue(), 1.0 / years) - 1) * 100;
        return BigDecimal.valueOf(cagr).setScale(2, RoundingMode.HALF_UP);
    }
}
