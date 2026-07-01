package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.portfolio.dto.LiquidityResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class LiquidityService {
    private static final BigDecimal PARTICIPATION_RATE = new BigDecimal("0.10");

    private final SecurityRepository securities;
    private final PriceQuoteRepository quotes;

    public LiquidityService(SecurityRepository securities, PriceQuoteRepository quotes) {
        this.securities = securities;
        this.quotes = quotes;
    }

    public LiquidityResult assess(String symbol, BigDecimal positionValue) {
        return securities.findBySymbol(symbol.toUpperCase())
                .map(security -> assess(security, positionValue))
                .orElse(new LiquidityResult(symbol, null, null, "UNKNOWN", "MISSING_SEEDED_HISTORY"));
    }

    private LiquidityResult assess(Security security, BigDecimal positionValue) {
        List<PriceQuote> recent = quotes.findBySecurityAndQuoteDateBetweenOrderByQuoteDateDesc(
                security, LocalDate.now().minusDays(90), LocalDate.now());
        BigDecimal averageDailyDollarVolume = averageDailyDollarVolume(recent);
        if (positionValue == null || averageDailyDollarVolume == null || averageDailyDollarVolume.signum() <= 0) {
            return new LiquidityResult(security.getSymbol(), averageDailyDollarVolume, null, "UNKNOWN", "DATA_UNAVAILABLE");
        }
        BigDecimal dailyCapacity = averageDailyDollarVolume.multiply(PARTICIPATION_RATE);
        BigDecimal days = positionValue.divide(dailyCapacity, 2, RoundingMode.HALF_UP);
        return new LiquidityResult(security.getSymbol(), money(averageDailyDollarVolume), days, classify(days), "AVAILABLE");
    }

    private BigDecimal averageDailyDollarVolume(List<PriceQuote> recent) {
        List<BigDecimal> values = recent.stream()
                .filter(q -> q.getClose() != null && q.getVolume() != null && q.getVolume() > 0)
                .map(q -> q.getClose().multiply(BigDecimal.valueOf(q.getVolume())))
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private String classify(BigDecimal days) {
        if (days.compareTo(new BigDecimal("5")) < 0) {
            return "LIQUID";
        }
        if (days.compareTo(new BigDecimal("20")) <= 0) {
            return "MODERATE";
        }
        return "ILLIQUID";
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
