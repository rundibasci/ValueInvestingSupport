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
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.marketdata.SourceTracker;
import it.mazzoni.vis.valuation.ValuationOutcome;
import it.mazzoni.vis.valuation.ValuationParams;
import it.mazzoni.vis.valuation.ValuationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Profile("!demo")
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);

    private final MarketDataClient marketDataClient;
    private final SecurityRepository securityRepository;
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final ValueScoreRepository valueScoreRepository;
    private final ValuationService valuationService;
    private final ValuationDefaultsProperties defaults;
    private final SourceTracker sourceTracker;

    public SeedService(MarketDataClient marketDataClient,
                       SecurityRepository securityRepository,
                       FundamentalSnapshotRepository fundamentalSnapshotRepository,
                       RatioSnapshotRepository ratioSnapshotRepository,
                       PriceQuoteRepository priceQuoteRepository,
                       ValueScoreRepository valueScoreRepository,
                       ValuationService valuationService,
                       ValuationDefaultsProperties defaults,
                       SourceTracker sourceTracker) {
        this.marketDataClient = marketDataClient;
        this.securityRepository = securityRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.valueScoreRepository = valueScoreRepository;
        this.valuationService = valuationService;
        this.defaults = defaults;
        this.sourceTracker = sourceTracker;
    }

    public List<SeedResult> seedTickers(List<String> symbols) {
        List<SeedResult> results = new ArrayList<>();
        for (String symbol : symbols) {
            String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                results.add(seedOne(normalized));
            }
        }
        return results;
    }

    @Transactional
    SeedResult seedOne(String symbol) {
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
            BigDecimal currentPrice = priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)
                    .map(PriceQuote::getClose)
                    .orElse(null);
            BigDecimal totalScore = valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(security)
                    .map(it.mazzoni.vis.domain.entity.ValueScore::getTotalScore)
                    .orElse(null);

            return SeedResult.success(symbol, security.getCompanyName(),
                    security.getSector(), security.getExchange(), security.getCountry(),
                    security.getDescription(), currentPrice,
                    result.getCompositeFairValue(), result.getMarginOfSafety(),
                    totalScore, result.getRecommendation(), sourceTracker.summarize(), LocalDate.now());

        } catch (MarketDataException e) {
            log.warn("Seed failed for {}: {}", symbol, e.getMessage());
            String errorMsg = switch (e.getErrorCode()) {
                case NOT_FOUND         -> "not found";
                case PLAN_RESTRICTION  -> "not available on current FMP plan";
                default                -> e.getMessage();
            };
            return SeedResult.failed(symbol, errorMsg);
        } catch (Exception e) {
            log.warn("Seed failed for {}: {}", symbol, e.getMessage());
            return SeedResult.failed(symbol, e.getMessage());
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
        if (fundamentalSnapshotRepository.existsBySecurityAndPeriodAndReportDate(security, Period.ANNUAL, today)) {
            return;
        }
        it.mazzoni.vis.domain.FundamentalSnapshot data = marketDataClient.getFundamentals(symbol);
        List<BigDecimal> fcfHistory = data.fcfHistory() != null ? data.fcfHistory() : List.of();
        int currentYear = today.getYear();
        for (int i = 0; i < Math.max(1, fcfHistory.size()); i++) {
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
                if (data.revenueHistory() != null && !data.revenueHistory().isEmpty())
                    entity.setRevenue(data.revenueHistory().get(0));
                if (data.netIncomeHistory() != null && !data.netIncomeHistory().isEmpty())
                    entity.setNetIncome(data.netIncomeHistory().get(0));
            }
            if (!fcfHistory.isEmpty())
                entity.setFreeCashFlow(fcfHistory.get(i));
            fundamentalSnapshotRepository.save(entity);
        }
    }

    private void persistRatios(Security security, String symbol) {
        LocalDate today = LocalDate.now();
        if (ratioSnapshotRepository.existsBySecurityAndPeriodAndReportDate(security, Period.TTM, today)) {
            return;
        }
        it.mazzoni.vis.domain.RatioSnapshot data = marketDataClient.getRatios(symbol);
        RatioSnapshot entity = new RatioSnapshot();
        entity.setSecurity(security);
        entity.setPeriod(Period.TTM);
        entity.setReportDate(today);
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
}
