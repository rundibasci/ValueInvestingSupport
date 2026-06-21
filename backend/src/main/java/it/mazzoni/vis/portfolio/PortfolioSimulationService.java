package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.domain.repository.WatchlistItemRepository;
import it.mazzoni.vis.portfolio.dto.AllocationWeight;
import it.mazzoni.vis.portfolio.dto.PortfolioSimulationResponse;
import it.mazzoni.vis.portfolio.dto.SimulationExclusion;
import it.mazzoni.vis.portfolio.dto.SimulationProposalItem;
import it.mazzoni.vis.portfolio.dto.SimulationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PortfolioSimulationService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String DISCLAIMER = "This is a decision-support tool, not investment advice (MiFID II).";

    private final PortfolioService portfolioService;
    private final UserRepository userRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final SecurityRepository securityRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final ValueScoreRepository valueScoreRepository;
    private final ValuationResultRepository valuationResultRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;

    public PortfolioSimulationService(PortfolioService portfolioService, UserRepository userRepository,
                                      WatchlistItemRepository watchlistItemRepository,
                                      SecurityRepository securityRepository,
                                      PriceQuoteRepository priceQuoteRepository,
                                      ValueScoreRepository valueScoreRepository,
                                      ValuationResultRepository valuationResultRepository,
                                      RatioSnapshotRepository ratioSnapshotRepository) {
        this.portfolioService = portfolioService;
        this.userRepository = userRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.securityRepository = securityRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.valueScoreRepository = valueScoreRepository;
        this.valuationResultRepository = valuationResultRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
    }

    @Transactional(readOnly = true)
    public PortfolioSimulationResponse simulate(Authentication auth, UUID portfolioId, SimulationRequest request) {
        // Reuse the ownership-safe F2 lookup; it deliberately returns 404 for another user's portfolio.
        portfolioService.getPortfolioDetail(auth, portfolioId);
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        BigDecimal stockCap = defaulted(request.maxStockPercent(), "25");
        BigDecimal sectorCap = defaulted(request.maxSectorPercent(), "40");
        BigDecimal countryCap = defaulted(request.maxCountryPercent(), "50");
        BigDecimal minMos = defaulted(request.minimumMarginOfSafety(), "0");
        BigDecimal minYield = request.minimumDividendYield();
        List<SimulationExclusion> excluded = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();

        watchlistItemRepository.findByWatchlist_UserOrderByAddedAtDesc(user).forEach(item -> {
            String symbol = item.getSymbol();
            Security security = securityRepository.findBySymbol(symbol).orElse(null);
            if (security == null) { excluded.add(new SimulationExclusion(symbol, "SECURITY_NOT_FOUND")); return; }
            PriceQuote quote = priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security).orElse(null);
            if (quote == null || quote.getClose() == null || quote.getClose().signum() <= 0) { excluded.add(new SimulationExclusion(symbol, "PRICE_UNAVAILABLE")); return; }
            ValueScore score = valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(security).orElse(null);
            if (score == null || score.getTotalScore() == null || score.getTotalScore().signum() <= 0) { excluded.add(new SimulationExclusion(symbol, "VALUE_SCORE_UNAVAILABLE")); return; }
            ValuationResult valuation = valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security).orElse(null);
            if (valuation == null || valuation.getMarginOfSafety() == null) { excluded.add(new SimulationExclusion(symbol, "MARGIN_OF_SAFETY_UNAVAILABLE")); return; }
            if (valuation.getMarginOfSafety().compareTo(minMos) < 0) { excluded.add(new SimulationExclusion(symbol, "BELOW_MINIMUM_MARGIN_OF_SAFETY")); return; }
            if (blank(security.getSector()) || blank(security.getCountry())) { excluded.add(new SimulationExclusion(symbol, "SECTOR_OR_COUNTRY_UNAVAILABLE")); return; }
            BigDecimal yield = ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(security)
                    .map(RatioSnapshot::getDividendYield).orElse(null);
            if (minYield != null && (yield == null || yield.compareTo(minYield) < 0)) { excluded.add(new SimulationExclusion(symbol, "BELOW_MINIMUM_DIVIDEND_YIELD")); return; }
            candidates.add(new Candidate(symbol, security.getSector(), security.getCountry(), quote.getClose(),
                    score.getTotalScore(), valuation.getMarginOfSafety(), yield));
        });

        candidates.sort(Comparator.comparing(Candidate::score).reversed().thenComparing(Candidate::symbol));
        excluded.sort(Comparator.comparing(SimulationExclusion::symbol));
        if (candidates.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No eligible watchlist candidates");

        allocate(candidates, request.budget(), stockCap, sectorCap, countryCap);
        List<Candidate> allocated = candidates.stream().filter(c -> c.shares > 0).toList();
        if (allocated.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Budget cannot purchase a share while respecting constraints");

        BigDecimal invested = allocated.stream().map(Candidate::actualAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cash = request.budget().subtract(invested).setScale(2, RoundingMode.HALF_UP);
        Map<String, BigDecimal> sectorAmounts = amountsBy(allocated, Candidate::sector);
        Map<String, BigDecimal> countryAmounts = amountsBy(allocated, Candidate::country);
        return new PortfolioSimulationResponse(portfolioId, money(request.budget()), stockCap, sectorCap, countryCap,
                money(invested), cash, weighted(allocated, Candidate::mos, invested), weighted(allocated, Candidate::yield, invested),
                allocated.stream().map(c -> new SimulationProposalItem(c.symbol, c.score, money(c.price), c.shares,
                        money(c.targetAmount), money(c.actualAmount()), percent(c.actualAmount(), request.budget()),
                        c.sector, c.country, c.mos, c.yield)).toList(), excluded,
                weights(sectorAmounts, request.budget()), weights(countryAmounts, request.budget()), DISCLAIMER);
    }

    private void allocate(List<Candidate> candidates, BigDecimal budget, BigDecimal stockCap, BigDecimal sectorCap, BigDecimal countryCap) {
        BigDecimal stockLimit = budget.multiply(stockCap).divide(HUNDRED, 8, RoundingMode.DOWN);
        BigDecimal sectorLimit = budget.multiply(sectorCap).divide(HUNDRED, 8, RoundingMode.DOWN);
        BigDecimal countryLimit = budget.multiply(countryCap).divide(HUNDRED, 8, RoundingMode.DOWN);
        Map<String, BigDecimal> sectorAllocated = new HashMap<>();
        Map<String, BigDecimal> countryAllocated = new HashMap<>();
        BigDecimal remaining = budget;
        List<Candidate> active = new ArrayList<>(candidates);
        while (!active.isEmpty() && remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalScore = active.stream().map(Candidate::score).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean progressed = false;
            for (Candidate candidate : new ArrayList<>(active)) {
                BigDecimal sectorRemaining = sectorLimit.subtract(sectorAllocated.getOrDefault(candidate.sector, BigDecimal.ZERO));
                BigDecimal countryRemaining = countryLimit.subtract(countryAllocated.getOrDefault(candidate.country, BigDecimal.ZERO));
                BigDecimal capacity = min(stockLimit.subtract(candidate.targetAmount), sectorRemaining, countryRemaining);
                BigDecimal desired = remaining.multiply(candidate.score).divide(totalScore, 8, RoundingMode.DOWN);
                BigDecimal addition = min(desired, capacity, remaining);
                if (addition.signum() > 0) {
                    candidate.targetAmount = candidate.targetAmount.add(addition);
                    sectorAllocated.merge(candidate.sector, addition, BigDecimal::add);
                    countryAllocated.merge(candidate.country, addition, BigDecimal::add);
                    remaining = remaining.subtract(addition);
                    progressed = true;
                }
                if (capacity.compareTo(addition) <= 0) active.remove(candidate);
            }
            if (!progressed) break;
        }
        for (Candidate candidate : candidates) candidate.shares = candidate.targetAmount.divide(candidate.price, 0, RoundingMode.DOWN).longValue();
        // Spend residual cash deterministically without ever breaching a cap.
        BigDecimal spent = candidates.stream().map(Candidate::actualAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cash = budget.subtract(spent);
        boolean bought;
        do {
            bought = false;
            for (Candidate c : candidates) {
                BigDecimal next = c.actualAmount().add(c.price);
                BigDecimal sectorAmount = candidates.stream().filter(x -> x.sector.equals(c.sector)).map(Candidate::actualAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal countryAmount = candidates.stream().filter(x -> x.country.equals(c.country)).map(Candidate::actualAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (cash.compareTo(c.price) >= 0 && next.compareTo(stockLimit) <= 0
                        && sectorAmount.add(c.price).compareTo(sectorLimit) <= 0
                        && countryAmount.add(c.price).compareTo(countryLimit) <= 0) { c.shares++; cash = cash.subtract(c.price); bought = true; }
            }
        } while (bought);
    }

    private static Map<String, BigDecimal> amountsBy(List<Candidate> candidates, java.util.function.Function<Candidate, String> key) {
        Map<String, BigDecimal> result = new HashMap<>();
        candidates.forEach(c -> result.merge(key.apply(c), c.actualAmount(), BigDecimal::add));
        return result;
    }
    private static List<AllocationWeight> weights(Map<String, BigDecimal> amounts, BigDecimal budget) {
        return amounts.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> new AllocationWeight(e.getKey(), percent(e.getValue(), budget))).toList();
    }
    private static BigDecimal weighted(List<Candidate> candidates, java.util.function.Function<Candidate, BigDecimal> value, BigDecimal invested) {
        BigDecimal total = BigDecimal.ZERO; boolean any = false;
        for (Candidate c : candidates) if (value.apply(c) != null) { total = total.add(c.actualAmount().multiply(value.apply(c))); any = true; }
        return any ? total.divide(invested, 4, RoundingMode.HALF_UP) : null;
    }
    private static BigDecimal defaulted(BigDecimal value, String fallback) { return value == null ? new BigDecimal(fallback) : value; }
    private static BigDecimal min(BigDecimal... values) { BigDecimal min = values[0]; for (BigDecimal value : values) if (value.compareTo(min) < 0) min = value; return min; }
    private static BigDecimal percent(BigDecimal amount, BigDecimal budget) { return amount.multiply(HUNDRED).divide(budget, 2, RoundingMode.HALF_UP); }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static final class Candidate {
        private final String symbol; private final String sector; private final String country; private final BigDecimal price; private final BigDecimal score; private final BigDecimal mos; private final BigDecimal yield;
        private BigDecimal targetAmount = BigDecimal.ZERO; private long shares;
        private Candidate(String symbol, String sector, String country, BigDecimal price, BigDecimal score, BigDecimal mos, BigDecimal yield) { this.symbol=symbol; this.sector=sector; this.country=country; this.price=price; this.score=score; this.mos=mos; this.yield=yield; }
        private String symbol() { return symbol; } private String sector() { return sector; } private String country() { return country; } private BigDecimal score() { return score; } private BigDecimal mos() { return mos; } private BigDecimal yield() { return yield; }
        private BigDecimal actualAmount() { return price.multiply(BigDecimal.valueOf(shares)); }
    }
}
