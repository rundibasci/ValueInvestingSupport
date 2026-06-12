package it.mazzoni.vis.demo;

import it.mazzoni.vis.adapter.YahooFinanceAdapter;
import it.mazzoni.vis.client.yahoo.YahooFinanceClient;
import it.mazzoni.vis.client.yahoo.dto.ChartResponse;
import it.mazzoni.vis.client.yahoo.dto.QuoteSummaryResponse;
import it.mazzoni.vis.demo.dto.DemoAnalysisResponse;
import it.mazzoni.vis.demo.dto.DemoAnalysisResponse.DcfValuation;
import it.mazzoni.vis.demo.dto.DemoAnalysisResponse.FinancialSummary;
import it.mazzoni.vis.demo.dto.DemoAnalysisResponse.Valuation;
import it.mazzoni.vis.demo.dto.Recommendation;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.valuation.DcfCalculator;
import it.mazzoni.vis.valuation.DcfInput;
import it.mazzoni.vis.valuation.DcfResult;
import it.mazzoni.vis.valuation.GrahamCalculator;
import it.mazzoni.vis.valuation.MarginOfSafetyCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class DemoAnalysisService {

    private static final BigDecimal WACC = new BigDecimal("0.10");
    private static final BigDecimal TERMINAL_RATE = new BigDecimal("0.03");
    private static final BigDecimal MAX_GROWTH = new BigDecimal("0.15");
    private static final BigDecimal DCF_WEIGHT = new BigDecimal("0.60");
    private static final BigDecimal GRAHAM_WEIGHT = new BigDecimal("0.40");

    private final YahooFinanceClient yahooClient;
    private final YahooFinanceAdapter adapter;
    private final DcfCalculator dcfCalculator;

    public DemoAnalysisService(YahooFinanceClient yahooClient, YahooFinanceAdapter adapter) {
        this.yahooClient = yahooClient;
        this.adapter = adapter;
        this.dcfCalculator = new DcfCalculator();
    }

    public DemoAnalysisResponse analyze(String symbol) {
        QuoteSummaryResponse qsr = yahooClient.getQuoteSummary(symbol);
        ChartResponse cr = yahooClient.getChart(symbol);

        FundamentalSnapshot snapshot = adapter.toFundamentalSnapshot(symbol, qsr, cr);

        BigDecimal grahamNumber = GrahamCalculator.calculate(
                snapshot.epsTtm(), snapshot.bookValuePerShare());

        Optional<DcfResult> dcfResult = computeDcf(snapshot);

        BigDecimal composite = buildComposite(dcfResult, grahamNumber);
        BigDecimal mos = MarginOfSafetyCalculator.compute(composite, snapshot.currentPrice());

        DcfValuation dcfValuation = dcfResult.map(r ->
                new DcfValuation(r.fairValue(), r.fairValueLow(), r.fairValueHigh())
        ).orElse(null);

        return new DemoAnalysisResponse(
                snapshot.symbol(),
                snapshot.companyName(),
                snapshot.currentPrice(),
                snapshot.currency(),
                snapshot.sector(),
                buildFinancialSummary(snapshot),
                new Valuation(dcfValuation, grahamNumber, composite),
                mos,
                toRecommendation(mos),
                DemoAnalysisResponse.DISCLAIMER
        );
    }

    private Optional<DcfResult> computeDcf(FundamentalSnapshot snapshot) {
        List<BigDecimal> fcfHistory = snapshot.fcfHistory();
        if (fcfHistory == null || fcfHistory.isEmpty()) return Optional.empty();
        if (snapshot.sharesOutstanding() == null || snapshot.sharesOutstanding() == 0) return Optional.empty();

        long positiveYears = fcfHistory.stream()
                .filter(f -> f.compareTo(BigDecimal.ZERO) > 0)
                .count();

        BigDecimal fcfTtm = fcfHistory.get(0);
        BigDecimal growthY1Y5 = estimateGrowth(fcfHistory);
        BigDecimal growthY6Y10 = growthY1Y5.divide(BigDecimal.TWO, 4, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);

        BigDecimal shares = BigDecimal.valueOf(snapshot.sharesOutstanding());
        BigDecimal netDebt = snapshot.netDebt() != null ? snapshot.netDebt() : BigDecimal.ZERO;

        DcfInput input = new DcfInput(
                fcfTtm, growthY1Y5, growthY6Y10, TERMINAL_RATE, WACC,
                shares, netDebt, (int) positiveYears);

        return dcfCalculator.calculate(input);
    }

    private BigDecimal estimateGrowth(List<BigDecimal> fcfHistory) {
        if (fcfHistory.size() < 2) return new BigDecimal("0.05");
        BigDecimal recent = fcfHistory.get(0);
        BigDecimal oldest = fcfHistory.get(fcfHistory.size() - 1);
        if (oldest.compareTo(BigDecimal.ZERO) <= 0 || recent.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("0.05");
        }
        // Simple CAGR over the available years
        int years = fcfHistory.size() - 1;
        double cagr = Math.pow(recent.doubleValue() / oldest.doubleValue(), 1.0 / years) - 1.0;
        BigDecimal growth = BigDecimal.valueOf(cagr).setScale(4, RoundingMode.HALF_UP);
        // Cap at MAX_GROWTH, floor at 0
        return growth.min(MAX_GROWTH).max(BigDecimal.ZERO);
    }

    private BigDecimal buildComposite(Optional<DcfResult> dcfResult, BigDecimal grahamNumber) {
        if (grahamNumber == null) return null;
        return dcfResult
                .map(r -> r.fairValue().multiply(DCF_WEIGHT)
                        .add(grahamNumber.multiply(GRAHAM_WEIGHT))
                        .setScale(2, RoundingMode.HALF_UP))
                .orElse(grahamNumber);
    }

    private FinancialSummary buildFinancialSummary(FundamentalSnapshot s) {
        BigDecimal revenue = firstOrNull(s.revenueHistory());
        BigDecimal netIncome = firstOrNull(s.netIncomeHistory());
        BigDecimal fcf = firstOrNull(s.fcfHistory());
        return new FinancialSummary(revenue, netIncome, fcf, s.epsTtm());
    }

    private static Recommendation toRecommendation(BigDecimal mos) {
        if (mos == null) return null;
        int cmp15 = mos.compareTo(new BigDecimal("15"));
        int cmp5 = mos.compareTo(new BigDecimal("5"));
        int cmp0 = mos.compareTo(BigDecimal.ZERO);
        if (cmp15 > 0) return Recommendation.QUALITY_VALUE;
        if (cmp5 > 0) return Recommendation.UNDERVALUED;
        if (cmp0 >= 0) return Recommendation.FAIRLY_VALUED;
        return Recommendation.OVERVALUED;
    }

    private static BigDecimal firstOrNull(List<BigDecimal> list) {
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }
}
