package it.mazzoni.vis.security;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.security.dto.GrowthMetrics;
import it.mazzoni.vis.security.dto.GrowthResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;

@Service
public class GrowthService {

    public GrowthResponse compute(String symbol, List<FundamentalSnapshot> annuals) {
        return new GrowthResponse(
                symbol,
                metrics(annuals, FundamentalSnapshot::getRevenue),
                metrics(annuals, FundamentalSnapshot::getFreeCashFlow),
                metrics(annuals, FundamentalSnapshot::getEps)
        );
    }

    private GrowthMetrics metrics(List<FundamentalSnapshot> annuals,
                                  Function<FundamentalSnapshot, BigDecimal> extractor) {
        return new GrowthMetrics(
                cagr(annuals, extractor, 3),
                cagr(annuals, extractor, 5),
                cagr(annuals, extractor, 10)
        );
    }

    private BigDecimal cagr(List<FundamentalSnapshot> annuals,
                             Function<FundamentalSnapshot, BigDecimal> extractor,
                             int years) {
        if (annuals.size() < years + 1) return null;

        BigDecimal end = extractor.apply(annuals.get(0));
        BigDecimal start = extractor.apply(annuals.get(years));

        if (end == null || start == null) return null;
        if (end.signum() <= 0 || start.signum() <= 0) return null;

        double ratio = end.doubleValue() / start.doubleValue();
        if (!Double.isFinite(ratio) || ratio <= 0) return null;

        double result = (Math.pow(ratio, 1.0 / years) - 1) * 100;
        if (!Double.isFinite(result)) return null;

        return BigDecimal.valueOf(result).setScale(2, RoundingMode.HALF_UP);
    }
}
