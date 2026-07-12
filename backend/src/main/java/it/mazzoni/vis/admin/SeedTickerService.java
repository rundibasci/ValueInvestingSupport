package it.mazzoni.vis.admin;

import it.mazzoni.vis.config.ValuationDefaultsProperties;
import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.SourceTracker;
import it.mazzoni.vis.scoring.ValueScoreService;
import it.mazzoni.vis.valuation.ValuationOutcome;
import it.mazzoni.vis.valuation.ValuationParams;
import it.mazzoni.vis.valuation.ValuationService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final ValuationService valuationService;
    private final ValueScoreService valueScoreService;
    private final ValuationDefaultsProperties defaults;
    private final SourceTracker sourceTracker;

    public SeedTickerService(MarketDataClient marketDataClient,
                             SecurityRepository securityRepository,
                             FundamentalSnapshotRepository fundamentalSnapshotRepository,
                             RatioSnapshotRepository ratioSnapshotRepository,
                             PriceQuoteRepository priceQuoteRepository,
                             ValuationService valuationService,
                             ValueScoreService valueScoreService,
                             ValuationDefaultsProperties defaults,
                             SourceTracker sourceTracker) {
        this.marketDataClient = marketDataClient;
        this.securityRepository = securityRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.valuationService = valuationService;
        this.valueScoreService = valueScoreService;
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
            persistPriceQuote(security, symbol);

            ValuationParams params = new ValuationParams(
                    defaults.wacc(), defaults.growthY1Y5(), defaults.growthY6Y10(),
                    defaults.terminalRate(), null, null);
            ValuationOutcome outcome = valuationService.calculate(symbol, params);
            ValuationResult result = outcome.result();
            it.mazzoni.vis.domain.entity.ValueScore score = valueScoreService.compute(symbol);
            BigDecimal currentPrice = priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)
                    .map(PriceQuote::getClose)
                    .orElse(null);
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
        security.setExchange(profile.exchange());
        security.setSector(profile.sector());
        security.setIndustry(profile.industry());
        security.setCountry(profile.country());
        security.setCurrency(profile.currency());
        if (profile.marketCap() != null) security.setMarketCap(profile.marketCap());
        return securityRepository.save(security);
    }

    private void persistFundamentals(Security security, String symbol) {
        LocalDate today = LocalDate.now();
        it.mazzoni.vis.domain.FundamentalSnapshot data = marketDataClient.getFundamentals(symbol);
        List<BigDecimal> revenueHistory = data.revenueHistory() != null ? data.revenueHistory() : List.of();
        List<BigDecimal> netIncomeHistory = data.netIncomeHistory() != null ? data.netIncomeHistory() : List.of();
        List<BigDecimal> fcfHistory = data.fcfHistory() != null ? data.fcfHistory() : List.of();
        int historySize = Math.max(1, Math.max(revenueHistory.size(), Math.max(netIncomeHistory.size(), fcfHistory.size())));
        int currentYear = today.getYear();

        fundamentalSnapshotRepository.deleteBySecurityAndPeriodAndFiscalYear(security, Period.ANNUAL, currentYear);
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
            if (i == 0) {
                entity.setEps(data.epsTtm());
                entity.setEpsDiluted(data.epsTtm());
                entity.setSharesOutstanding(data.sharesOutstanding());
                entity.setTotalDebt(data.totalDebt());
                entity.setCash(data.cash());
                if (data.bookValuePerShare() != null && data.sharesOutstanding() != null) {
                    entity.setTotalEquity(data.bookValuePerShare()
                            .multiply(BigDecimal.valueOf(data.sharesOutstanding())));
                }
            }
            entity.setRevenue(valueAt(revenueHistory, i));
            entity.setNetIncome(valueAt(netIncomeHistory, i));
            entity.setFreeCashFlow(valueAt(fcfHistory, i));
            fundamentalSnapshotRepository.save(entity);
        }

        FundamentalSnapshot ttm = new FundamentalSnapshot();
        ttm.setSecurity(security);
        ttm.setPeriod(Period.TTM);
        ttm.setFiscalYear(currentYear);
        ttm.setReportDate(today);
        ttm.setRevenue(valueAt(revenueHistory, 0));
        ttm.setNetIncome(valueAt(netIncomeHistory, 0));
        ttm.setFreeCashFlow(valueAt(fcfHistory, 0));
        ttm.setEps(data.epsTtm());
        ttm.setEpsDiluted(data.epsTtm());
        ttm.setSharesOutstanding(data.sharesOutstanding());
        ttm.setTotalDebt(data.totalDebt());
        ttm.setCash(data.cash());
        if (data.bookValuePerShare() != null && data.sharesOutstanding() != null) {
            ttm.setTotalEquity(data.bookValuePerShare()
                    .multiply(BigDecimal.valueOf(data.sharesOutstanding())));
        }
        fundamentalSnapshotRepository.save(ttm);
    }

    private void persistRatios(Security security, String symbol) {
        it.mazzoni.vis.domain.RatioSnapshot data = marketDataClient.getRatios(symbol);
        ratioSnapshotRepository.deleteBySecurityAndPeriod(security, Period.TTM);
        ratioSnapshotRepository.deleteAll(ratioSnapshotRepository
                .findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)
                .stream()
                .filter(snapshot -> sameRatioValues(snapshot, data))
                .toList());

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
        entity.setDebtToEquity(data.debtToEquity());
        entity.setDividendYield(data.dividendYield());
        entity.setPayoutRatio(data.payoutRatio());
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
                && sameDecimal(snapshot.getDebtToEquity(), data.debtToEquity())
                && sameDecimal(snapshot.getDividendYield(), data.dividendYield())
                && sameDecimal(snapshot.getPayoutRatio(), data.payoutRatio());
    }

    private static boolean sameDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private void persistPriceQuote(Security security, String symbol) {
        LocalDate today = LocalDate.now();
        if (priceQuoteRepository.existsBySecurityAndQuoteDate(security, today)) {
            return;
        }
        it.mazzoni.vis.domain.MarketPriceQuote quote = marketDataClient.getQuote(symbol);
        if (quote.price() == null) return;
        PriceQuote entity = new PriceQuote();
        entity.setSecurity(security);
        entity.setQuoteDate(today);
        entity.setClose(quote.price());
        priceQuoteRepository.save(entity);
    }

    private static BigDecimal valueAt(List<BigDecimal> values, int index) {
        return index < values.size() ? values.get(index) : null;
    }
}
