package it.mazzoni.vis.security;

import it.mazzoni.vis.common.dto.AvailabilityResponse;
import it.mazzoni.vis.domain.entity.DividendRecord;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.GrahamChecklistItem;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.entity.WaccResultEntity;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.GrahamChecklistItemRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.domain.repository.WaccResultRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.scoring.dto.ValueScoreResponse;
import it.mazzoni.vis.security.domain.AnalystEstimate;
import it.mazzoni.vis.security.domain.AnalystEstimateRepository;
import it.mazzoni.vis.security.dto.AnalystEstimatesItem;
import it.mazzoni.vis.security.dto.AnnualFinancials;
import it.mazzoni.vis.security.dto.DcfScenarios;
import it.mazzoni.vis.security.dto.DividendItem;
import it.mazzoni.vis.security.dto.DividendsResponse;
import it.mazzoni.vis.security.dto.FinancialsResponse;
import it.mazzoni.vis.security.dto.GrowthResponse;
import it.mazzoni.vis.security.dto.PeerItem;
import it.mazzoni.vis.security.dto.PeersResponse;
import it.mazzoni.vis.security.dto.QuarterlyFinancials;
import it.mazzoni.vis.security.dto.RatioSnapshotItem;
import it.mazzoni.vis.security.dto.RatiosHistoryResponse;
import it.mazzoni.vis.security.dto.SecurityDetailResponse;
import it.mazzoni.vis.security.dto.SecurityReviewResponse;
import it.mazzoni.vis.security.dto.TtmFinancials;
import it.mazzoni.vis.security.dto.ValuationDetailResponse;
import it.mazzoni.vis.valuation.DcfInput;
import it.mazzoni.vis.valuation.DcfSensitivityCell;
import it.mazzoni.vis.valuation.DcfSensitivityResult;
import it.mazzoni.vis.valuation.DcfSensitivityService;
import it.mazzoni.vis.valuation.MarginOfSafetyCalculator;
import it.mazzoni.vis.valuation.StaleDataException;
import it.mazzoni.vis.valuation.ValuationDataUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SecurityReviewService {

    private static final int STALE_DAYS = 7;
    private static final BigDecimal DEFAULT_GROWTH_Y1_Y5 = new BigDecimal("0.06");
    private static final BigDecimal DEFAULT_GROWTH_Y6_Y10 = new BigDecimal("0.04");
    private static final BigDecimal DEFAULT_TERMINAL_RATE = new BigDecimal("0.025");

    private final SecurityRepository securityRepository;
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final ValuationResultRepository valuationResultRepository;
    private final DividendRecordRepository dividendRecordRepository;
    private final ValueScoreRepository valueScoreRepository;
    private final WaccResultRepository waccResultRepository;
    private final GrahamChecklistItemRepository grahamChecklistItemRepository;
    private final AnalystEstimateRepository analystEstimateRepository;
    private final DividendsService dividendsService;
    private final GrowthService growthService;

    public SecurityReviewService(SecurityRepository securityRepository,
                                 FundamentalSnapshotRepository fundamentalSnapshotRepository,
                                 RatioSnapshotRepository ratioSnapshotRepository,
                                 PriceQuoteRepository priceQuoteRepository,
                                 ValuationResultRepository valuationResultRepository,
                                 DividendRecordRepository dividendRecordRepository,
                                 ValueScoreRepository valueScoreRepository,
                                 WaccResultRepository waccResultRepository,
                                 GrahamChecklistItemRepository grahamChecklistItemRepository,
                                 AnalystEstimateRepository analystEstimateRepository,
                                 DividendsService dividendsService,
                                 GrowthService growthService) {
        this.securityRepository = securityRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.valuationResultRepository = valuationResultRepository;
        this.dividendRecordRepository = dividendRecordRepository;
        this.valueScoreRepository = valueScoreRepository;
        this.waccResultRepository = waccResultRepository;
        this.grahamChecklistItemRepository = grahamChecklistItemRepository;
        this.analystEstimateRepository = analystEstimateRepository;
        this.dividendsService = dividendsService;
        this.growthService = growthService;
    }

    @Transactional(readOnly = true)
    public SecurityReviewResponse getReview(String symbol) {
        String upper = symbol.toUpperCase();
        Security security = securityRepository.findBySymbol(upper)
                .orElseThrow(() -> new SymbolNotFoundException(symbol));

        FundamentalSnapshot latestAnnual = fundamentalSnapshotRepository
                .findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)
                .orElseThrow(() -> new ValuationDataUnavailableException(symbol));

        if (latestAnnual.getReportDate() != null &&
                latestAnnual.getReportDate().isBefore(LocalDate.now().minusDays(STALE_DAYS))) {
            throw new StaleDataException(symbol, latestAnnual.getReportDate());
        }

        List<FundamentalSnapshot> annualSnapshots = fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL);
        List<FundamentalSnapshot> reviewAnnuals = annualSnapshots.stream().limit(10).toList();
        List<FundamentalSnapshot> growthAnnuals = annualSnapshots.stream().limit(11).toList();
        List<RatioSnapshot> ratioSnapshots = ratioSnapshotRepository
                .findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)
                .stream()
                .limit(10)
                .toList();
        RatioSnapshot latestRatios = ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(security).orElse(null);
        PriceQuote latestPrice = priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security).orElse(null);
        ValuationResult latestValuation = valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security).orElse(null);
        ValueScore latestScore = valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(security).orElse(null);
        List<DividendRecord> dividendRecords = dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security);

        SecurityDetailResponse detail = SecurityDetailResponse.from(security, latestAnnual, latestRatios, latestPrice);
        FinancialsResponse financials = financials(upper, reviewAnnuals);
        RatiosHistoryResponse ratios = new RatiosHistoryResponse(upper, ratioSnapshots.stream().map(RatioSnapshotItem::from).toList());
        ValuationDetailResponse valuation = latestValuation != null ? valuation(security, latestValuation, upper, latestAnnual, reviewAnnuals) : null;
        DividendsResponse dividends = dividends(upper, dividendRecords);
        GrowthResponse growth = growthService.compute(upper, growthAnnuals);
        PeersResponse peers = peers(security);
        ValueScoreResponse score = latestScore != null ? ValueScoreResponse.from(latestScore) : null;
        SecurityReviewResponse.FinancialHealth financialHealth = financialHealth(latestAnnual, latestRatios);

        return new SecurityReviewResponse(
                upper,
                detail,
                financials,
                ratios,
                valuation,
                dividends,
                growth,
                peers,
                score,
                financialHealth,
                sourceCoverage(latestAnnual, latestRatios, latestPrice, latestValuation, latestScore, dividends, peers),
                freshness(latestAnnual, latestRatios, latestPrice, latestValuation, latestScore, dividends),
                availability(latestAnnual, latestRatios, latestPrice, latestValuation, latestScore, dividends),
                dataQualityNotes(financialHealth, valuation, latestValuation, latestScore, dividends)
        );
    }

    private FinancialsResponse financials(String symbol, List<FundamentalSnapshot> annualSnapshots) {
        List<AnnualFinancials> annuals = annualSnapshots.stream()
                .map(AnnualFinancials::from)
                .toList();
        List<QuarterlyFinancials> quarters = fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(annualSnapshots.get(0).getSecurity(), Period.QUARTERLY)
                .stream()
                .limit(8)
                .map(QuarterlyFinancials::from)
                .toList();
        TtmFinancials ttm = fundamentalSnapshotRepository
                .findTopBySecurityAndPeriodOrderByReportDateDesc(annualSnapshots.get(0).getSecurity(), Period.TTM)
                .map(TtmFinancials::from)
                .orElse(null);
        return new FinancialsResponse(symbol, annuals, quarters, ttm);
    }

    private DividendsResponse dividends(String symbol, List<DividendRecord> records) {
        return new DividendsResponse(
                symbol,
                records.stream().map(DividendItem::from).toList(),
                dividendsService.computeStreak(records),
                dividendsService.computeCagr(records, 3),
                dividendsService.computeCagr(records, 5),
                dividendsService.computeCagr(records, 10)
        );
    }

    private ValuationDetailResponse valuation(Security security,
                                              ValuationResult result,
                                              String symbol,
                                              FundamentalSnapshot latestAnnual,
                                              List<FundamentalSnapshot> annualSnapshots) {
        WaccResultEntity wacc = waccResultRepository.findByValuationResult(result).orElse(null);
        List<GrahamChecklistItem> checklist = grahamChecklistItemRepository.findByValuationResultOrderByCriterionCodeAsc(result);
        return new ValuationDetailResponse(
                security.getSymbol(),
                security.getCompanyName(),
                result.getCurrentPrice(),
                new DcfScenarios(result.getDcfFairValue(), result.getDcfFairValueLow(), result.getDcfFairValueHigh()),
                result.getDcfTerminalValuePercentage(),
                result.isDcfHighTerminalDependence(),
                sensitivity(result, latestAnnual, annualSnapshots, wacc),
                result.getGrahamNumber(),
                result.getDdmFairValue(),
                result.getEpvFairValue() != null
                        ? new ValuationDetailResponse.EpvDetail(result.getEpvFairValue(), result.getEpvNormalizedEarnings(), result.getEpvYearsAveraged())
                        : null,
                result.getOwnerEarnings() != null
                        ? new ValuationDetailResponse.OwnerEarningsDetail(result.getOwnerEarnings(), result.getMaintenanceCapexEstimate())
                        : null,
                result.getCompositeFairValue(),
                result.getMarginOfSafety(),
                computeMos(result.getDcfFairValueLow(), result.getCurrentPrice()),
                computeMos(result.getDcfFairValueHigh(), result.getCurrentPrice()),
                result.getRecommendation() != null ? result.getRecommendation().name() : null,
                buildAnalystEstimates(symbol),
                waccDetail(wacc),
                grahamChecklist(checklist),
                result.getValuationDate(),
                ValuationDetailResponse.MIFID_DISCLAIMER
        );
    }

    private ValuationDetailResponse.WaccDetail waccDetail(WaccResultEntity wacc) {
        if (wacc == null) return null;
        return new ValuationDetailResponse.WaccDetail(
                wacc.getWacc(),
                wacc.getRiskFreeRate(),
                wacc.getEquityRiskPremium(),
                wacc.getBeta(),
                wacc.getCostOfEquity(),
                wacc.getCostOfDebt(),
                wacc.getDebtWeight(),
                wacc.getEquityWeight(),
                wacc.getEffectiveTaxRate(),
                wacc.isFallbackUsed(),
                wacc.getSource()
        );
    }

    private ValuationDetailResponse.GrahamChecklistDetail grahamChecklist(List<GrahamChecklistItem> items) {
        if (items.isEmpty()) return null;
        int passed = (int) items.stream().filter(item -> "PASS".equals(item.getStatus())).count();
        int failed = (int) items.stream().filter(item -> "FAIL".equals(item.getStatus())).count();
        int insufficient = items.size() - passed - failed;
        return new ValuationDetailResponse.GrahamChecklistDetail(
                passed,
                failed,
                insufficient,
                items.stream()
                        .map(item -> new ValuationDetailResponse.GrahamChecklistCriterion(
                                item.getCriterionCode(),
                                item.getLabel(),
                                item.getStatus(),
                                item.getActualValue()))
                        .toList()
        );
    }

    private ValuationDetailResponse.DcfSensitivity sensitivity(ValuationResult result,
                                                               FundamentalSnapshot latestAnnual,
                                                               List<FundamentalSnapshot> annualSnapshots,
                                                               WaccResultEntity wacc) {
        if (result.getDcfFairValue() == null || latestAnnual.getFreeCashFlow() == null
                || latestAnnual.getSharesOutstanding() == null || latestAnnual.getSharesOutstanding() <= 0
                || wacc == null || wacc.getWacc() == null) {
            return null;
        }
        BigDecimal netDebt = latestAnnual.getTotalDebt() != null && latestAnnual.getCash() != null
                ? latestAnnual.getTotalDebt().subtract(latestAnnual.getCash())
                : BigDecimal.ZERO;
        int positiveFcfYears = (int) annualSnapshots.stream()
                .filter(snapshot -> snapshot.getFreeCashFlow() != null && snapshot.getFreeCashFlow().compareTo(BigDecimal.ZERO) > 0)
                .count();
        DcfInput input = new DcfInput(
                latestAnnual.getFreeCashFlow(),
                DEFAULT_GROWTH_Y1_Y5,
                DEFAULT_GROWTH_Y6_Y10,
                DEFAULT_TERMINAL_RATE,
                wacc.getWacc(),
                BigDecimal.valueOf(latestAnnual.getSharesOutstanding()),
                netDebt,
                positiveFcfYears
        );
        DcfSensitivityResult sensitivity = new DcfSensitivityService().analyze(input);
        return new ValuationDetailResponse.DcfSensitivity(
                sensitivity.waccValues(),
                sensitivity.terminalRateValues(),
                sensitivity.cells().stream()
                        .map(this::sensitivityCell)
                        .toList(),
                wacc.getWacc(),
                DEFAULT_TERMINAL_RATE
        );
    }

    private ValuationDetailResponse.DcfSensitivityCell sensitivityCell(DcfSensitivityCell cell) {
        return new ValuationDetailResponse.DcfSensitivityCell(
                cell.wacc(),
                cell.terminalRate(),
                cell.fairValue(),
                cell.terminalValuePercentage(),
                cell.highTerminalDependence()
        );
    }

    private BigDecimal computeMos(BigDecimal fairValue, BigDecimal currentPrice) {
        if (fairValue == null || currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) == 0) return null;
        return MarginOfSafetyCalculator.compute(fairValue, currentPrice);
    }

    private AnalystEstimatesItem buildAnalystEstimates(String symbol) {
        List<AnalystEstimate> estimates = analystEstimateRepository.findBySecuritySymbolOrderByTargetDateDesc(symbol);
        if (estimates.isEmpty()) return null;

        List<BigDecimal> prices = estimates.stream()
                .map(AnalystEstimate::getTargetPrice)
                .filter(p -> p != null)
                .toList();
        if (prices.isEmpty()) return null;

        BigDecimal mean = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP);
        BigDecimal low = prices.stream().min(Comparator.naturalOrder()).orElse(null);
        BigDecimal high = prices.stream().max(Comparator.naturalOrder()).orElse(null);
        Map<String, Long> ratingCounts = estimates.stream()
                .filter(e -> e.getRatingLabel() != null)
                .collect(Collectors.groupingBy(AnalystEstimate::getRatingLabel, Collectors.counting()));

        return new AnalystEstimatesItem(mean, low, high, estimates.size(), resolveConsensus(ratingCounts));
    }

    private String resolveConsensus(Map<String, Long> counts) {
        for (String rating : List.of("BUY", "HOLD", "SELL")) {
            long count = counts.getOrDefault(rating, 0L);
            long others = counts.values().stream().mapToLong(Long::longValue).sum() - count;
            if (count > others) return rating;
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private PeersResponse peers(Security subject) {
        if (subject.getSector() == null) return new PeersResponse(subject.getSymbol(), List.of());

        BigDecimal subjectCap = subject.getMarketCap() != null ? subject.getMarketCap() : BigDecimal.ZERO;
        List<PeerItem> peers = securityRepository
                .findBySectorAndSymbolNot(subject.getSector(), subject.getSymbol())
                .stream()
                .sorted(Comparator.comparing(p -> {
                    BigDecimal cap = p.getMarketCap() != null ? p.getMarketCap() : BigDecimal.ZERO;
                    return cap.subtract(subjectCap).abs();
                }))
                .limit(5)
                .map(this::peerItem)
                .toList();

        return new PeersResponse(subject.getSymbol(), peers);
    }

    private PeerItem peerItem(Security peer) {
        ValuationResult valuation = valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(peer).orElse(null);
        ValueScore score = valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(peer).orElse(null);
        RatioSnapshot ratios = ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(peer).orElse(null);

        return new PeerItem(
                peer.getSymbol(),
                peer.getCompanyName(),
                valuation != null ? valuation.getCurrentPrice() : null,
                valuation != null ? valuation.getCompositeFairValue() : null,
                valuation != null ? valuation.getMarginOfSafety() : null,
                score != null ? score.getTotalScore() : null,
                ratios != null ? ratios.getPeRatio() : null,
                ratios != null ? ratios.getRoic() : null
        );
    }

    private SecurityReviewResponse.FinancialHealth financialHealth(FundamentalSnapshot latestAnnual,
                                                                   RatioSnapshot latestRatios) {
        BigDecimal netDebt = null;
        if (latestAnnual.getTotalDebt() != null && latestAnnual.getCash() != null) {
            netDebt = latestAnnual.getTotalDebt().subtract(latestAnnual.getCash());
        }
        return new SecurityReviewResponse.FinancialHealth(
                latestAnnual.getTotalDebt(),
                latestAnnual.getCash(),
                netDebt,
                latestRatios != null ? latestRatios.getDebtToEquity() : null,
                latestRatios != null ? latestRatios.getCurrentRatio() : null,
                null,
                null,
                latestRatios != null ? latestRatios.getPayoutRatio() : null,
                latestRatios != null ? latestRatios.getDividendYield() : null,
                latestRatios != null ? latestRatios.getGrossMargin() : null,
                latestRatios != null ? latestRatios.getOperatingMargin() : null,
                latestRatios != null ? latestRatios.getNetMargin() : null,
                latestAnnual.getReportDate()
        );
    }

    private List<SecurityReviewResponse.SourceCoverageItem> sourceCoverage(FundamentalSnapshot annual,
                                                                            RatioSnapshot ratios,
                                                                            PriceQuote price,
                                                                            ValuationResult valuation,
                                                                            ValueScore score,
                                                                            DividendsResponse dividends,
                                                                            PeersResponse peers) {
        List<SecurityReviewResponse.SourceCoverageItem> items = new ArrayList<>();
        items.add(coverage("Profile", annual != null, null));
        items.add(coverage("Fundamentals", annual != null, null));
        items.add(coverage("Ratios", ratios != null, null));
        items.add(coverage("Quote", price != null, null));
        items.add(coverage("Dividends", !dividends.history().isEmpty(), null));
        items.add(coverage("Valuation", valuation != null, valuation != null ? valuation.getSource() : null));
        items.add(coverage("Score", score != null, null));
        items.add(coverage("Peers", !peers.peers().isEmpty(), null));
        items.add(coverage("Analyst estimates", buildAnalystEstimates(annual.getSecurity().getSymbol()) != null, null));
        return items;
    }

    private SecurityReviewResponse.SourceCoverageItem coverage(String category, boolean available, String provider) {
        String normalizedProvider = provider != null && !provider.isBlank() ? provider : null;
        if (available) {
            return new SecurityReviewResponse.SourceCoverageItem(
                    category,
                    normalizedProvider,
                    "AVAILABLE",
                    normalizedProvider != null
                            ? "Provider metadata is available for this category."
                            : "Application data is available; provider-level metadata is not stored for this category."
            );
        }
        return new SecurityReviewResponse.SourceCoverageItem(
                category,
                normalizedProvider,
                "MISSING_SEEDED_HISTORY",
                "No local data is available for this category."
        );
    }

    private List<SecurityReviewResponse.FreshnessItem> freshness(FundamentalSnapshot annual,
                                                                  RatioSnapshot ratios,
                                                                  PriceQuote price,
                                                                  ValuationResult valuation,
                                                                  ValueScore score,
                                                                  DividendsResponse dividends) {
        return List.of(
                freshness("Fundamentals", annual.getReportDate()),
                freshness("Ratios", ratios != null ? ratios.getReportDate() : null),
                freshness("Quote", price != null ? price.getQuoteDate() : null),
                freshness("Valuation", valuation != null ? valuation.getValuationDate() : null),
                freshness("Score", score != null ? score.getScoreDate() : null),
                freshness("Dividends", dividends.history().stream()
                        .map(DividendItem::exDividendDate)
                        .filter(d -> d != null)
                        .max(Comparator.naturalOrder())
                        .orElse(null))
        );
    }

    private SecurityReviewResponse.FreshnessItem freshness(String category, LocalDate date) {
        if (date == null) {
            return new SecurityReviewResponse.FreshnessItem(category, null, "MISSING_SEEDED_HISTORY", "No local date is available.");
        }
        boolean stale = date.isBefore(LocalDate.now().minusDays(STALE_DAYS));
        return new SecurityReviewResponse.FreshnessItem(
                category,
                date,
                stale ? "STALE" : "FRESH",
                stale ? "This category is older than the configured freshness guard." : "Local data is within the freshness guard."
        );
    }

    private List<SecurityReviewResponse.AvailabilityItem> availability(FundamentalSnapshot annual,
                                                                        RatioSnapshot ratios,
                                                                        PriceQuote price,
                                                                        ValuationResult valuation,
                                                                        ValueScore score,
                                                                        DividendsResponse dividends) {
        return List.of(
                availability("Fundamentals", annual != null, annual != null ? annual.getReportDate() : null, "No seeded fundamental history is available."),
                availability("Ratios", ratios != null, ratios != null ? ratios.getReportDate() : null, "No seeded ratio history is available."),
                availability("Quote", price != null, price != null ? price.getQuoteDate() : null, "No local quote is available."),
                valuationAvailability(valuation),
                score != null
                        ? new SecurityReviewResponse.AvailabilityItem("Score", AvailabilityResponse.available(score.getScoreDate()))
                        : new SecurityReviewResponse.AvailabilityItem("Score", AvailabilityResponse.missingComputation("No persisted value score is available.")),
                dividends.history().isEmpty()
                        ? new SecurityReviewResponse.AvailabilityItem("Dividends", AvailabilityResponse.providerLimited("Dividend history is unavailable from the current local data."))
                        : new SecurityReviewResponse.AvailabilityItem("Dividends", AvailabilityResponse.available(dividends.history().get(0).exDividendDate()))
        );
    }

    private SecurityReviewResponse.AvailabilityItem availability(String category, boolean present, LocalDate date, String missingReason) {
        return new SecurityReviewResponse.AvailabilityItem(
                category,
                present ? AvailabilityResponse.available(date) : new AvailabilityResponse(it.mazzoni.vis.common.AvailabilityStatus.MISSING_SEEDED_HISTORY, missingReason, null)
        );
    }

    private SecurityReviewResponse.AvailabilityItem valuationAvailability(ValuationResult valuation) {
        if (valuation == null) {
            return new SecurityReviewResponse.AvailabilityItem("Valuation",
                    AvailabilityResponse.missingComputation("No persisted valuation result is available."));
        }
        if (valuation.getDcfFairValue() == null) {
            return new SecurityReviewResponse.AvailabilityItem("Valuation",
                    new AvailabilityResponse(it.mazzoni.vis.common.AvailabilityStatus.GUARDRAIL_BLOCKED,
                            "DCF is unavailable because eligibility guardrails were not met; other valuation components may still be available.",
                            valuation.getValuationDate()));
        }
        return new SecurityReviewResponse.AvailabilityItem("Valuation",
                AvailabilityResponse.available(valuation.getValuationDate()));
    }

    private List<SecurityReviewResponse.DataQualityNote> dataQualityNotes(SecurityReviewResponse.FinancialHealth health,
                                                                           ValuationDetailResponse valuation,
                                                                           ValuationResult rawValuation,
                                                                           ValueScore score,
                                                                           DividendsResponse dividends) {
        List<SecurityReviewResponse.DataQualityNote> notes = new ArrayList<>();
        if (health.quickRatio() == null) notes.add(note("Financial health", "INFO", "Quick ratio is unavailable from the current provider data model."));
        if (health.interestCoverage() == null) notes.add(note("Financial health", "INFO", "Interest coverage is unavailable from the current provider data model."));
        if (valuation == null) notes.add(note("Valuation", "WARNING", "No persisted valuation result is available for this symbol."));
        if (rawValuation != null && rawValuation.getDcfFairValue() == null) notes.add(note("Valuation", "INFO", "DCF output is unavailable, likely because eligibility guards were not met."));
        if (score == null) notes.add(note("Score", "INFO", "No persisted value score is available for this symbol."));
        if (dividends.history().isEmpty()) notes.add(note("Dividends", "INFO", "Dividend history is unavailable for this symbol."));
        notes.add(note("Advice boundary", "INFO", "Fair value, margin of safety, recommendation, and score are decision-support outputs, not investment advice."));
        return notes;
    }

    private SecurityReviewResponse.DataQualityNote note(String category, String severity, String message) {
        return new SecurityReviewResponse.DataQualityNote(category, severity, message);
    }
}
