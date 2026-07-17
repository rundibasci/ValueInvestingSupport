package it.mazzoni.vis.admin;

import it.mazzoni.vis.config.ValuationDefaultsProperties;
import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.entity.DividendRecord;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.InsiderTrade;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.TransactionType;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.InsiderTradeRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.marketdata.SourceTracker;
import it.mazzoni.vis.marketdata.fmp.dto.FmpDividendEntry;
import it.mazzoni.vis.marketdata.fmp.dto.FmpInsiderTradingEntry;
import it.mazzoni.vis.moat.CapitalAllocationService;
import it.mazzoni.vis.moat.MoatAssessmentService;
import it.mazzoni.vis.moat.RoicObservationService;
import it.mazzoni.vis.scoring.RiskAnalysisService;
import it.mazzoni.vis.scoring.ValueScoreService;
import it.mazzoni.vis.valuation.ValuationOutcome;
import it.mazzoni.vis.valuation.ValuationNotApplicableException;
import it.mazzoni.vis.valuation.ValuationParams;
import it.mazzoni.vis.valuation.ValuationService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@Profile("!demo")
public class SeedTickerService {

    private final MarketDataClient marketDataClient;
    private final SecurityRepository securityRepository;
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final DividendRecordRepository dividendRecordRepository;
    private final InsiderTradeRepository insiderTradeRepository;
    private final ValuationService valuationService;
    private final ValueScoreService valueScoreService;
    private final RiskAnalysisService riskAnalysisService;
    private final MoatAssessmentService moatAssessmentService;
    private final CapitalAllocationService capitalAllocationService;
    private final RoicObservationService roicObservationService;
    private final ValuationDefaultsProperties defaults;
    private final SourceTracker sourceTracker;

    public SeedTickerService(MarketDataClient marketDataClient,
                             SecurityRepository securityRepository,
                             FundamentalSnapshotRepository fundamentalSnapshotRepository,
                             RatioSnapshotRepository ratioSnapshotRepository,
                             PriceQuoteRepository priceQuoteRepository,
                             DividendRecordRepository dividendRecordRepository,
                             InsiderTradeRepository insiderTradeRepository,
                             ValuationService valuationService,
                             ValueScoreService valueScoreService,
                             RiskAnalysisService riskAnalysisService,
                             MoatAssessmentService moatAssessmentService,
                             CapitalAllocationService capitalAllocationService,
                             RoicObservationService roicObservationService,
                             ValuationDefaultsProperties defaults,
                             SourceTracker sourceTracker) {
        this.marketDataClient = marketDataClient;
        this.securityRepository = securityRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.dividendRecordRepository = dividendRecordRepository;
        this.insiderTradeRepository = insiderTradeRepository;
        this.valuationService = valuationService;
        this.valueScoreService = valueScoreService;
        this.riskAnalysisService = riskAnalysisService;
        this.moatAssessmentService = moatAssessmentService;
        this.capitalAllocationService = capitalAllocationService;
        this.roicObservationService = roicObservationService;
        this.defaults = defaults;
        this.sourceTracker = sourceTracker;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeedResult seedOne(String symbol) {
        sourceTracker.clear();
        try {
            Security security = upsertSecurity(symbol);
            persistFundamentals(security, symbol);
            persistRatios(security, symbol);
            roicObservationService.refreshAfterIngestion(security, sourceTracker.summarize());
            persistPriceQuote(security, symbol);
            enrichDerivedProfileData(security);
            persistDividends(security, symbol);
            persistInsiderTrades(security, symbol);

            ValuationParams params = new ValuationParams(
                    defaults.wacc(), defaults.growthY1Y5(), defaults.growthY6Y10(),
                    defaults.terminalRate(), null, null);
            ValuationOutcome outcome;
            try {
                outcome = valuationService.calculate(symbol, params);
            } catch (ValuationNotApplicableException exception) {
                recomputeDerivedAnalytics(security, symbol);
                BigDecimal currentPrice = currentPrice(security);
                return SeedResult.partial(symbol, security.getCompanyName(),
                        security.getSector(), security.getExchange(), security.getCountry(),
                        security.getDescription(), currentPrice, sourceTracker.summarize(), LocalDate.now());
            }
            ValuationResult result = outcome.result();
            it.mazzoni.vis.domain.entity.ValueScore score = valueScoreService.compute(symbol);
            recomputeDerivedAnalytics(security, symbol);
            BigDecimal currentPrice = currentPrice(security);
            BigDecimal totalScore = score.getTotalScore();

            return SeedResult.success(symbol, security.getCompanyName(),
                    security.getSector(), security.getExchange(), security.getCountry(),
                    security.getDescription(), currentPrice,
                    result.getCompositeFairValue(), result.getMarginOfSafety(),
                    totalScore, result.getRecommendation(), sourceTracker.summarize(), LocalDate.now());
        } finally {
            sourceTracker.clear();
        }
    }

    private BigDecimal currentPrice(Security security) {
        return priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)
                .map(PriceQuote::getClose)
                .orElse(null);
    }

    private void recomputeDerivedAnalytics(Security security, String symbol) {
        riskAnalysisService.computePiotroski(symbol);
        riskAnalysisService.computeAltman(symbol);
        riskAnalysisService.assessCyclicality(symbol);
        riskAnalysisService.computeEarningsQuality(symbol);
        moatAssessmentService.analyze(security);
        capitalAllocationService.analyze(security);
    }

    private Security upsertSecurity(String symbol) {
        CompanyProfile profile = marketDataClient.getProfile(symbol);
        Security security = securityRepository.findBySymbol(symbol)
                .orElseGet(() -> {
                    Security s = new Security();
                    s.setSymbol(symbol);
                    return s;
                });
        if (profile.companyName() != null) security.setCompanyName(profile.companyName());
        else if (security.getCompanyName() == null) security.setCompanyName(symbol);
        security.setActive(true);
        if (hasText(profile.exchange())) security.setExchange(profile.exchange());
        security.setSector(profile.sector());
        security.setIndustry(profile.industry());
        security.setCountry(profile.country());
        security.setCurrency(profile.currency());
        if (profile.marketCap() != null) security.setMarketCap(profile.marketCap());
        if (hasText(profile.description())) security.setDescription(profile.description());
        if (hasText(profile.website())) security.setWebsite(profile.website());
        return securityRepository.save(security);
    }

    private void persistFundamentals(Security security, String symbol) {
        LocalDate today = LocalDate.now();
        it.mazzoni.vis.domain.FundamentalSnapshot data = marketDataClient.getFundamentals(symbol);
        List<BigDecimal> revenueHistory = data.revenueHistory() != null ? data.revenueHistory() : List.of();
        List<BigDecimal> netIncomeHistory = data.netIncomeHistory() != null ? data.netIncomeHistory() : List.of();
        List<BigDecimal> fcfHistory = data.fcfHistory() != null ? data.fcfHistory() : List.of();
        List<BigDecimal> epsHistory = data.epsHistory() != null ? data.epsHistory() : List.of();
        List<Long> sharesHistory = data.sharesOutstandingHistory() != null ? data.sharesOutstandingHistory() : List.of();
        List<BigDecimal> operatingIncomeHistory = data.operatingIncomeHistory() != null ? data.operatingIncomeHistory() : List.of();
        List<BigDecimal> operatingCashFlowHistory = data.operatingCashFlowHistory() != null ? data.operatingCashFlowHistory() : List.of();
        List<BigDecimal> totalAssetsHistory = data.totalAssetsHistory() != null ? data.totalAssetsHistory() : List.of();
        List<BigDecimal> totalLiabilitiesHistory = data.totalLiabilitiesHistory() != null ? data.totalLiabilitiesHistory() : List.of();
        List<BigDecimal> totalDebtHistory = data.totalDebtHistory() != null ? data.totalDebtHistory() : List.of();
        List<BigDecimal> cashHistory = data.cashHistory() != null ? data.cashHistory() : List.of();
        List<BigDecimal> totalEquityHistory = data.totalEquityHistory() != null ? data.totalEquityHistory() : List.of();
        List<BigDecimal> pretaxIncomeHistory = data.pretaxIncomeHistory() != null ? data.pretaxIncomeHistory() : List.of();
        List<BigDecimal> incomeTaxExpenseHistory = data.incomeTaxExpenseHistory() != null ? data.incomeTaxExpenseHistory() : List.of();
        int historySize = List.of(revenueHistory.size(), netIncomeHistory.size(), fcfHistory.size(), epsHistory.size(),
                        sharesHistory.size(), operatingIncomeHistory.size(), operatingCashFlowHistory.size(),
                        totalAssetsHistory.size(), totalLiabilitiesHistory.size(), totalDebtHistory.size(),
                        cashHistory.size(), totalEquityHistory.size(), pretaxIncomeHistory.size(), incomeTaxExpenseHistory.size())
                .stream().mapToInt(Integer::intValue).max().orElse(1);
        int currentYear = today.getYear();

        fundamentalSnapshotRepository.deleteBySecurityAndPeriod(security, Period.ANNUAL);
        fundamentalSnapshotRepository.deleteBySecurityAndPeriod(security, Period.TTM);

        for (int i = 0; i < historySize; i++) {
            LocalDate reportDate = today.minusYears(i);
            if (fundamentalSnapshotRepository.existsBySecurityAndPeriodAndReportDate(security, Period.ANNUAL, reportDate)) {
                continue;
            }
            FundamentalSnapshot entity = new FundamentalSnapshot();
            entity.setSecurity(security);
            entity.setPeriod(Period.ANNUAL);
            entity.setFiscalYear(currentYear - i);
            entity.setReportDate(reportDate);
            Long annualShares = firstNonNull(longAt(sharesHistory, i),
                    estimateShares(valueAt(netIncomeHistory, i), valueAt(epsHistory, i)));
            if (annualShares != null) {
                entity.setSharesOutstanding(annualShares);
            }
            entity.setRevenue(valueAt(revenueHistory, i));
            entity.setNetIncome(valueAt(netIncomeHistory, i));
            entity.setOperatingIncome(valueAt(operatingIncomeHistory, i));
            entity.setPretaxIncome(valueAt(pretaxIncomeHistory, i));
            entity.setIncomeTaxExpense(valueAt(incomeTaxExpenseHistory, i));
            entity.setOperatingCashFlow(valueAt(operatingCashFlowHistory, i));
            entity.setFreeCashFlow(valueAt(fcfHistory, i));
            entity.setEps(valueAt(epsHistory, i));
            entity.setEpsDiluted(valueAt(epsHistory, i));
            entity.setTotalAssets(valueAt(totalAssetsHistory, i));
            entity.setTotalLiabilities(valueAt(totalLiabilitiesHistory, i));
            entity.setTotalDebt(firstNonNull(valueAt(totalDebtHistory, i), i == 0 ? data.totalDebt() : null));
            entity.setCash(firstNonNull(valueAt(cashHistory, i), i == 0 ? data.cash() : null));
            BigDecimal totalEquity = valueAt(totalEquityHistory, i);
            if (totalEquity == null && i == 0) {
                Long sharesForEquity = annualShares != null ? annualShares : data.sharesOutstanding();
                if (data.bookValuePerShare() != null && sharesForEquity != null) {
                    totalEquity = data.bookValuePerShare().multiply(BigDecimal.valueOf(sharesForEquity));
                }
            }
            entity.setTotalEquity(totalEquity);
            fundamentalSnapshotRepository.save(entity);
        }

        FundamentalSnapshot ttm = new FundamentalSnapshot();
        ttm.setSecurity(security);
        ttm.setPeriod(Period.TTM);
        ttm.setFiscalYear(currentYear);
        ttm.setReportDate(today);
        ttm.setRevenue(valueAt(revenueHistory, 0));
        ttm.setNetIncome(valueAt(netIncomeHistory, 0));
        ttm.setOperatingIncome(valueAt(operatingIncomeHistory, 0));
        ttm.setPretaxIncome(valueAt(pretaxIncomeHistory, 0));
        ttm.setIncomeTaxExpense(valueAt(incomeTaxExpenseHistory, 0));
        ttm.setOperatingCashFlow(valueAt(operatingCashFlowHistory, 0));
        ttm.setFreeCashFlow(valueAt(fcfHistory, 0));
        ttm.setEps(data.epsTtm());
        ttm.setEpsDiluted(data.epsTtm());
        ttm.setSharesOutstanding(data.sharesOutstanding());
        ttm.setTotalDebt(data.totalDebt());
        ttm.setCash(data.cash());
        ttm.setTotalAssets(valueAt(totalAssetsHistory, 0));
        ttm.setTotalLiabilities(valueAt(totalLiabilitiesHistory, 0));
        ttm.setTotalEquity(firstNonNull(valueAt(totalEquityHistory, 0),
                data.bookValuePerShare() != null && data.sharesOutstanding() != null
                        ? data.bookValuePerShare().multiply(BigDecimal.valueOf(data.sharesOutstanding()))
                        : null));
        fundamentalSnapshotRepository.save(ttm);
    }

    private void persistRatios(Security security, String symbol) {
        List<it.mazzoni.vis.domain.RatioSnapshot> annualRatios = marketDataClient.getAnnualRatios(symbol);
        if (annualRatios == null || annualRatios.isEmpty()) {
            annualRatios = List.of(marketDataClient.getRatios(symbol));
        }
        it.mazzoni.vis.domain.RatioSnapshot data = annualRatios.get(0);
        ratioSnapshotRepository.deleteBySecurityAndPeriod(security, Period.TTM);
        ratioSnapshotRepository.deleteBySecurityAndPeriod(security, Period.ANNUAL);

        LocalDate today = LocalDate.now();
        for (int i = 0; i < annualRatios.size(); i++) {
            persistRatioSnapshot(security, annualRatios.get(i), Period.ANNUAL, today.minusYears(i));
        }
        persistRatioSnapshot(security, data, Period.TTM, LocalDate.now());
    }

    private void persistRatioSnapshot(Security security,
                                      it.mazzoni.vis.domain.RatioSnapshot data,
                                      Period period,
                                      LocalDate reportDate) {
        if (ratioSnapshotRepository.existsBySecurityAndPeriodAndReportDate(security, period, reportDate)) {
            return;
        }

        RatioSnapshot entity = new RatioSnapshot();
        entity.setSecurity(security);
        entity.setPeriod(period);
        entity.setReportDate(reportDate);
        entity.setPeRatio(data.peRatio());
        entity.setPbRatio(data.priceToBook());
        entity.setRoe(data.roe());
        entity.setRoa(data.roa());
        entity.setRoic(data.roic());
        entity.setCurrentRatio(data.currentRatio());
        entity.setQuickRatio(data.quickRatio());
        entity.setDebtToEquity(data.debtToEquity());
        entity.setInterestCoverage(data.interestCoverage());
        entity.setDividendYield(data.dividendYield());
        entity.setPayoutRatio(data.payoutRatio());
        entity.setGrossMargin(data.grossMargin());
        entity.setOperatingMargin(data.operatingMargin());
        entity.setNetMargin(data.netMargin());
        ratioSnapshotRepository.save(entity);
    }

    private static boolean sameRatioValues(RatioSnapshot snapshot,
                                           it.mazzoni.vis.domain.RatioSnapshot data) {
        return sameDecimal(snapshot.getPeRatio(), data.peRatio())
                && sameDecimal(snapshot.getPbRatio(), data.priceToBook())
                && sameDecimal(snapshot.getRoe(), data.roe())
                && sameDecimal(snapshot.getRoa(), data.roa())
                && sameDecimal(snapshot.getRoic(), data.roic())
                && sameDecimal(snapshot.getCurrentRatio(), data.currentRatio())
                && sameDecimal(snapshot.getQuickRatio(), data.quickRatio())
                && sameDecimal(snapshot.getDebtToEquity(), data.debtToEquity())
                && sameDecimal(snapshot.getInterestCoverage(), data.interestCoverage())
                && sameDecimal(snapshot.getDividendYield(), data.dividendYield())
                && sameDecimal(snapshot.getPayoutRatio(), data.payoutRatio())
                && sameDecimal(snapshot.getGrossMargin(), data.grossMargin())
                && sameDecimal(snapshot.getOperatingMargin(), data.operatingMargin())
                && sameDecimal(snapshot.getNetMargin(), data.netMargin());
    }

    private static boolean sameDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private void persistPriceQuote(Security security, String symbol) {
        LocalDate today = LocalDate.now();
        it.mazzoni.vis.domain.MarketPriceQuote quote = marketDataClient.getQuote(symbol);
        if (quote.price() == null) return;
        PriceQuote entity = priceQuoteRepository.findBySecurityAndQuoteDate(security, today)
                .orElseGet(() -> {
                    PriceQuote created = new PriceQuote();
                    created.setSecurity(security);
                    created.setQuoteDate(today);
                    return created;
                });
        entity.setClose(quote.price());
        entity.setVolume(quote.volume());
        priceQuoteRepository.save(entity);
    }

    private void enrichDerivedProfileData(Security security) {
        if (security.getMarketCap() != null) return;
        PriceQuote quote = priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security).orElse(null);
        FundamentalSnapshot annual = fundamentalSnapshotRepository
                .findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)
                .orElse(null);
        if (quote == null || quote.getClose() == null || annual == null
                || annual.getSharesOutstanding() == null || annual.getSharesOutstanding() <= 0) return;
        security.setMarketCap(quote.getClose().multiply(BigDecimal.valueOf(annual.getSharesOutstanding())));
        securityRepository.save(security);
    }

    private void persistDividends(Security security, String symbol) {
        List<FmpDividendEntry> entries;
        try {
            entries = marketDataClient.getDividendHistory(symbol);
        } catch (MarketDataException | UnsupportedOperationException e) {
            return;
        }

        for (FmpDividendEntry entry : entries) {
            if (entry.date() == null || entry.dividend() == null) {
                continue;
            }
            LocalDate exDate;
            try {
                exDate = LocalDate.parse(entry.date());
            } catch (Exception ignored) {
                continue;
            }
            if (dividendRecordRepository.findBySecurityAndExDividendDate(security, exDate).isPresent()) {
                continue;
            }

            DividendRecord record = new DividendRecord();
            record.setSecurity(security);
            record.setExDividendDate(exDate);
            record.setAmount(entry.dividend());
            record.setCurrency(security.getCurrency());
            if (entry.paymentDate() != null && !entry.paymentDate().isBlank()) {
                try {
                    record.setPaymentDate(LocalDate.parse(entry.paymentDate()));
                } catch (Exception ignored) {}
            }
            dividendRecordRepository.save(record);
        }
    }

    private void persistInsiderTrades(Security security, String symbol) {
        List<FmpInsiderTradingEntry> entries;
        try {
            entries = marketDataClient.getInsiderTransactions(symbol);
        } catch (MarketDataException | UnsupportedOperationException e) {
            return;
        }

        for (FmpInsiderTradingEntry entry : entries) {
            if (entry.transactionDate() == null || entry.reportingName() == null) {
                continue;
            }
            LocalDate tradeDate;
            try {
                tradeDate = LocalDate.parse(entry.transactionDate());
            } catch (Exception ignored) {
                continue;
            }
            if (insiderTradeRepository.existsBySecurityAndTradeDateAndInsiderName(
                    security, tradeDate, entry.reportingName())) {
                continue;
            }

            InsiderTrade trade = new InsiderTrade();
            trade.setSecurity(security);
            trade.setTradeDate(tradeDate);
            trade.setInsiderName(entry.reportingName());
            trade.setTitle(entry.title());
            trade.setTransactionType(resolveType(entry.transactionType()));
            trade.setShares(entry.securitiesTransacted());
            trade.setPricePerShare(entry.price());
            if (entry.securitiesTransacted() != null && entry.price() != null) {
                trade.setTradeValue(entry.price().multiply(BigDecimal.valueOf(entry.securitiesTransacted())));
            }
            insiderTradeRepository.save(trade);
        }
    }

    private static TransactionType resolveType(String fmpType) {
        if (fmpType == null) return TransactionType.BUY;
        String upper = fmpType.toUpperCase();
        return upper.contains("SALE") || upper.contains("SELL") || upper.startsWith("S-")
                ? TransactionType.SELL
                : TransactionType.BUY;
    }

    private static BigDecimal valueAt(List<BigDecimal> values, int index) {
        return index < values.size() ? values.get(index) : null;
    }

    private static Long longAt(List<Long> values, int index) {
        return index < values.size() ? values.get(index) : null;
    }

    private static Long estimateShares(BigDecimal netIncome, BigDecimal eps) {
        if (netIncome == null || eps == null || eps.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return netIncome.divide(eps, 0, RoundingMode.HALF_UP).longValue();
    }

    private static <T> T firstNonNull(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
