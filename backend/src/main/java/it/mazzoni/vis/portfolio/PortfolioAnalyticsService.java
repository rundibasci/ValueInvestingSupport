package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.portfolio.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PortfolioAnalyticsService {
    private static final BigDecimal SECTOR_CONCENTRATION_THRESHOLD = new BigDecimal("40.00");
    private static final BigDecimal IMMATERIAL_THRESHOLD = new BigDecimal("3.00");
    private static final BigDecimal CONCENTRATED_THRESHOLD = new BigDecimal("20.00");

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final UserRepository users;
    private final SecurityRepository securities;
    private final PriceQuoteRepository quotes;
    private final ValuationResultRepository valuations;
    private final RatioSnapshotRepository ratios;
    private final ValueScoreRepository scores;
    private final PiotroskiResultRepository piotroski;
    private final MoatResultRepository moats;
    private final EarningsQualityResultRepository earningsQuality;
    private final PortfolioAnalyticsSnapshotRepository snapshots;
    private final LiquidityService liquidity;
    private final BenchmarkService benchmarks;

    public PortfolioAnalyticsService(PortfolioRepository portfolios, HoldingRepository holdings, UserRepository users,
                                     SecurityRepository securities, PriceQuoteRepository quotes,
                                     ValuationResultRepository valuations, RatioSnapshotRepository ratios,
                                     ValueScoreRepository scores, PiotroskiResultRepository piotroski,
                                     MoatResultRepository moats, EarningsQualityResultRepository earningsQuality,
                                     PortfolioAnalyticsSnapshotRepository snapshots, LiquidityService liquidity,
                                     BenchmarkService benchmarks) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.users = users;
        this.securities = securities;
        this.quotes = quotes;
        this.valuations = valuations;
        this.ratios = ratios;
        this.scores = scores;
        this.piotroski = piotroski;
        this.moats = moats;
        this.earningsQuality = earningsQuality;
        this.snapshots = snapshots;
        this.liquidity = liquidity;
        this.benchmarks = benchmarks;
    }

    @Transactional
    public PortfolioAnalyticsResponse analyze(Authentication auth, UUID portfolioId) {
        Portfolio portfolio = portfolio(auth, portfolioId);
        List<Holding> portfolioHoldings = holdings.findByPortfolio(portfolio);
        List<AnalyticsWarning> warnings = new ArrayList<>();
        Map<String, HoldingInput> inputs = inputs(portfolioHoldings, warnings);
        BigDecimal total = inputs.values().stream().map(HoldingInput::currentValue)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hasPricedHolding = inputs.values().stream().anyMatch(input -> input.currentValue() != null);
        BigDecimal totalMarketValue = hasPricedHolding ? money(total) : null;

        Map<String, BigDecimal> weights = weights(inputs, total);
        WeightedMetricsResponse weighted = weightedMetrics(inputs, weights);
        Map<String, BigDecimal> sectorWeights = sectorWeights(inputs, weights);
        List<String> sectorFlags = sectorWeights.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(SECTOR_CONCENTRATION_THRESHOLD) > 0)
                .map(Map.Entry::getKey)
                .toList();
        sectorFlags.forEach(sector -> warnings.add(new AnalyticsWarning("SECTOR_CONCENTRATION", sector,
                sector + " exceeds the 40% portfolio concentration threshold.")));

        List<HoldingConcentrationResponse> holdingConcentration = holdingConcentration(weights, warnings);
        MoatProfileResponse moatProfile = moatProfile(inputs, weights);
        QualityDistributionResponse quality = qualityDistribution(inputs, weights);
        List<LiquidityResult> liquidityResults = inputs.values().stream()
                .map(input -> liquidity.assess(input.symbol(), input.currentValue()))
                .toList();
        liquidityResults.stream().filter(result -> "ILLIQUID".equals(result.classification()))
                .forEach(result -> warnings.add(new AnalyticsWarning("ILLIQUID_HOLDING", result.symbol(),
                        result.symbol() + " would take more than 20 trading days to liquidate at the default participation rate.")));

        BenchmarkComparisonResponse benchmark = benchmarks.compare(weighted, sectorWeights, "SPY");
        PortfolioAnalyticsSnapshot snapshot = new PortfolioAnalyticsSnapshot();
        snapshot.setPortfolio(portfolio);
        snapshot.setBenchmarkSymbol(benchmark.benchmarkSymbol());
        snapshot.setTotalMarketValue(totalMarketValue);
        snapshot.setWarningCount(warnings.size());
        snapshot.setPayload("portfolioId=" + portfolioId + "; weightedMetrics=" + weighted + "; warnings=" + warnings);
        PortfolioAnalyticsSnapshot saved = snapshots.save(snapshot);

        return new PortfolioAnalyticsResponse(portfolioId, totalMarketValue, weighted, sectorWeights, sectorFlags,
                holdingConcentration, moatProfile, quality, liquidityResults, benchmark, warnings,
                saved.getId(), saved.getCapturedAt() == null ? LocalDateTime.now() : saved.getCapturedAt());
    }

    private Portfolio portfolio(Authentication auth, UUID id) {
        User user = users.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return portfolios.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found: " + id));
    }

    private Map<String, HoldingInput> inputs(List<Holding> portfolioHoldings, List<AnalyticsWarning> warnings) {
        Map<String, HoldingInput> result = new TreeMap<>();
        for (Holding holding : portfolioHoldings) {
            String symbol = holding.getSymbol().toUpperCase();
            Optional<Security> security = securities.findBySymbol(symbol);
            if (security.isEmpty()) {
                warnings.add(new AnalyticsWarning("MISSING_SECURITY", symbol, symbol + " is not seeded in the security universe."));
                result.put(symbol, new HoldingInput(symbol, holding.getQuantity(), null, null, null, null, null, null, null, null, null, null));
                continue;
            }
            Security sec = security.get();
            BigDecimal price = quotes.findTopBySecurityOrderByQuoteDateDesc(sec).map(PriceQuote::getClose).orElse(null);
            BigDecimal currentValue = price == null ? null : holding.getQuantity().multiply(price);
            if (price == null) {
                warnings.add(new AnalyticsWarning("MISSING_PRICE", symbol, symbol + " has no current quote for weighting."));
            }
            RatioSnapshot ratio = ratios.findBySecurityAndPeriodOrderByReportDateDesc(sec, Period.ANNUAL)
                    .stream().findFirst().orElseGet(() -> ratios.findTopBySecurityOrderByReportDateDesc(sec).orElse(null));
            result.put(symbol, new HoldingInput(symbol, holding.getQuantity(), sec.getSector(), price, currentValue,
                    valuations.findTopBySecurityOrderByValuationDateDesc(sec).orElse(null),
                    ratio,
                    scores.findTopBySecurityOrderByScoreDateDesc(sec).orElse(null),
                    piotroski.findTopBySecurityOrderByResultDateDesc(sec).orElse(null),
                    moats.findTopBySecurityOrderByResultDateDesc(sec).orElse(null),
                    earningsQuality.findTopBySecurityOrderByResultDateDesc(sec).orElse(null),
                    sec));
        }
        return result;
    }

    private Map<String, BigDecimal> weights(Map<String, HoldingInput> inputs, BigDecimal total) {
        if (total.signum() <= 0) {
            return Map.of();
        }
        Map<String, BigDecimal> result = new TreeMap<>();
        inputs.forEach((symbol, input) -> {
            if (input.currentValue() != null) {
                result.put(symbol, input.currentValue().divide(total, 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
            }
        });
        return result;
    }

    private WeightedMetricsResponse weightedMetrics(Map<String, HoldingInput> inputs, Map<String, BigDecimal> weights) {
        return new WeightedMetricsResponse(
                weighted(inputs, weights, input -> input.valuation() == null ? null : input.valuation().getMarginOfSafety()),
                weighted(inputs, weights, input -> input.ratio() == null ? null : input.ratio().getPeRatio()),
                weighted(inputs, weights, input -> input.ratio() == null ? null : input.ratio().getDividendYield()),
                weighted(inputs, weights, input -> input.score() == null ? null : input.score().getTotalScore()),
                weighted(inputs, weights, input -> input.piotroski() == null ? null : BigDecimal.valueOf(input.piotroski().getTotalScore()))
        );
    }

    private BigDecimal weighted(Map<String, HoldingInput> inputs, Map<String, BigDecimal> weights,
                                java.util.function.Function<HoldingInput, BigDecimal> extractor) {
        BigDecimal value = BigDecimal.ZERO;
        boolean found = false;
        for (Map.Entry<String, BigDecimal> entry : weights.entrySet()) {
            BigDecimal metric = extractor.apply(inputs.get(entry.getKey()));
            if (metric != null) {
                value = value.add(entry.getValue().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP).multiply(metric));
                found = true;
            }
        }
        return found ? value.setScale(2, RoundingMode.HALF_UP) : null;
    }

    private Map<String, BigDecimal> sectorWeights(Map<String, HoldingInput> inputs, Map<String, BigDecimal> weights) {
        Map<String, BigDecimal> result = new TreeMap<>();
        weights.forEach((symbol, weight) -> result.merge(
                inputs.get(symbol).sector() == null ? "UNKNOWN" : inputs.get(symbol).sector(),
                weight, BigDecimal::add));
        result.replaceAll((sector, weight) -> weight.setScale(2, RoundingMode.HALF_UP));
        return result;
    }

    private List<HoldingConcentrationResponse> holdingConcentration(Map<String, BigDecimal> weights,
                                                                    List<AnalyticsWarning> warnings) {
        return weights.entrySet().stream().map(entry -> {
            String status = "NORMAL";
            if (entry.getValue().compareTo(IMMATERIAL_THRESHOLD) < 0) {
                status = "IMMATERIAL";
            } else if (entry.getValue().compareTo(CONCENTRATED_THRESHOLD) > 0) {
                status = "CONCENTRATED";
            }
            if (!"NORMAL".equals(status)) {
                warnings.add(new AnalyticsWarning("HOLDING_" + status, entry.getKey(),
                        entry.getKey() + " is " + status.toLowerCase(Locale.ROOT) + " by portfolio weight."));
            }
            return new HoldingConcentrationResponse(entry.getKey(), entry.getValue(), status);
        }).toList();
    }

    private MoatProfileResponse moatProfile(Map<String, HoldingInput> inputs, Map<String, BigDecimal> weights) {
        Map<String, BigDecimal> byMoat = new HashMap<>();
        weights.forEach((symbol, weight) -> {
            MoatResult moat = inputs.get(symbol).moat();
            String key = moat == null || moat.getMoatStrength() == null ? "UNKNOWN" : moat.getMoatStrength().name();
            byMoat.merge(key, weight, BigDecimal::add);
        });
        return new MoatProfileResponse(percent(byMoat.get("WIDE")), percent(byMoat.get("NARROW")),
                percent(byMoat.get("NONE")), percent(byMoat.get("UNKNOWN")));
    }

    private QualityDistributionResponse qualityDistribution(Map<String, HoldingInput> inputs, Map<String, BigDecimal> weights) {
        Map<String, BigDecimal> quality = weights.entrySet().stream()
                .filter(entry -> inputs.get(entry.getKey()).earningsQuality() != null)
                .collect(Collectors.groupingBy(entry -> inputs.get(entry.getKey()).earningsQuality().getClassification().name(),
                        TreeMap::new, Collectors.mapping(Map.Entry::getValue,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        quality.replaceAll((key, value) -> percent(value));
        return new QualityDistributionResponse(
                weighted(inputs, weights, input -> input.ratio() == null ? null : input.ratio().getRoic()),
                weighted(inputs, weights, input -> input.ratio() == null ? null : input.ratio().getRoe()),
                quality);
    }

    private BigDecimal percent(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record HoldingInput(String symbol, BigDecimal quantity, String sector, BigDecimal price,
                                BigDecimal currentValue, ValuationResult valuation, RatioSnapshot ratio,
                                ValueScore score, PiotroskiResult piotroski, MoatResult moat,
                                EarningsQualityResult earningsQuality, Security security) {
    }
}
