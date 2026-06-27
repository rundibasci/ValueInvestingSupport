package it.mazzoni.vis.localstack;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Holding;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.Portfolio;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.entity.Watchlist;
import it.mazzoni.vis.domain.entity.WatchlistItem;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.HoldingRepository;
import it.mazzoni.vis.domain.repository.PortfolioRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.domain.repository.WatchlistItemRepository;
import it.mazzoni.vis.domain.repository.WatchlistRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
@Profile("localstack")
public class LocalStackMarketDataSeeder {

    private final UserRepository users;
    private final SecurityRepository securities;
    private final PriceQuoteRepository quotes;
    private final ValuationResultRepository valuations;
    private final ValueScoreRepository scores;
    private final RatioSnapshotRepository ratios;
    private final FundamentalSnapshotRepository fundamentals;
    private final WatchlistRepository watchlists;
    private final WatchlistItemRepository watchlistItems;
    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;

    public LocalStackMarketDataSeeder(UserRepository users,
                                      SecurityRepository securities,
                                      PriceQuoteRepository quotes,
                                      ValuationResultRepository valuations,
                                      ValueScoreRepository scores,
                                      RatioSnapshotRepository ratios,
                                      FundamentalSnapshotRepository fundamentals,
                                      WatchlistRepository watchlists,
                                      WatchlistItemRepository watchlistItems,
                                      PortfolioRepository portfolios,
                                      HoldingRepository holdings) {
        this.users = users;
        this.securities = securities;
        this.quotes = quotes;
        this.valuations = valuations;
        this.scores = scores;
        this.ratios = ratios;
        this.fundamentals = fundamentals;
        this.watchlists = watchlists;
        this.watchlistItems = watchlistItems;
        this.portfolios = portfolios;
        this.holdings = holdings;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(20)
    @Transactional
    public void seed() {
        User user = users.findByEmail("admin@localstack.local").orElse(null);
        if (user == null) {
            return;
        }

        List<DemoSecurity> demo = List.of(
                new DemoSecurity("KO", "Coca-Cola Co.", "NYSE", "Consumer Defensive", "Beverages", "US",
                        "USD", "270000000000", "60.00", "72.00", "20.00", "74.50", "2.90"),
                new DemoSecurity("JNJ", "Johnson & Johnson", "NYSE", "Healthcare", "Drug Manufacturers", "US",
                        "USD", "380000000000", "150.00", "184.00", "18.48", "78.00", "3.10"),
                new DemoSecurity("MSFT", "Microsoft Corp.", "NASDAQ", "Technology", "Software", "US",
                        "USD", "3300000000000", "420.00", "470.00", "10.64", "69.00", "0.80")
        );

        for (DemoSecurity item : demo) {
            Security security = seedSecurity(item);
            seedQuote(security, item);
            seedValuation(security, item);
            seedScore(security, item);
            seedRatio(security, item);
            seedFundamentals(security, item);
        }

        Watchlist watchlist = seedWatchlist(user);
        for (DemoSecurity item : demo) {
            seedWatchlistItem(user, watchlist, item.symbol());
        }
        seedPortfolio(user);
    }

    private Security seedSecurity(DemoSecurity item) {
        return securities.findBySymbol(item.symbol()).orElseGet(() -> {
            Security security = new Security();
            security.setSymbol(item.symbol());
            security.setCompanyName(item.companyName());
            security.setExchange(item.exchange());
            security.setSector(item.sector());
            security.setIndustry(item.industry());
            security.setCountry(item.country());
            security.setCurrency(item.currency());
            security.setMarketCap(decimal(item.marketCap()));
            security.setWebsite("https://example.com/" + item.symbol().toLowerCase());
            security.setDescription(item.companyName() + " seeded for the localstack clickable demo.");
            return securities.save(security);
        });
    }

    private void seedQuote(Security security, DemoSecurity item) {
        LocalDate today = LocalDate.now();
        if (quotes.existsBySecurityAndQuoteDate(security, today)) {
            return;
        }
        PriceQuote quote = new PriceQuote();
        quote.setSecurity(security);
        quote.setQuoteDate(today);
        quote.setOpen(decimal(item.price()));
        quote.setHigh(decimal(item.price()).multiply(new BigDecimal("1.01")));
        quote.setLow(decimal(item.price()).multiply(new BigDecimal("0.99")));
        quote.setClose(decimal(item.price()));
        quote.setAdjustedClose(decimal(item.price()));
        quote.setVolume(1_000_000L);
        quotes.save(quote);
    }

    private void seedValuation(Security security, DemoSecurity item) {
        if (valuations.findTopBySecurityOrderByValuationDateDesc(security).isPresent()) {
            return;
        }
        ValuationResult valuation = new ValuationResult();
        valuation.setSecurity(security);
        valuation.setValuationDate(LocalDate.now());
        valuation.setCurrentPrice(decimal(item.price()));
        valuation.setCompositeFairValue(decimal(item.fairValue()));
        valuation.setDcfFairValue(decimal(item.fairValue()));
        valuation.setDcfFairValueLow(decimal(item.fairValue()).multiply(new BigDecimal("0.92")));
        valuation.setDcfFairValueHigh(decimal(item.fairValue()).multiply(new BigDecimal("1.08")));
        valuation.setGrahamNumber(decimal(item.fairValue()).multiply(new BigDecimal("0.78")));
        valuation.setMarginOfSafety(decimal(item.marginOfSafety()));
        valuation.setRecommendation(Recommendation.QUALITY_VALUE);
        valuation.setSource("localstack");
        valuations.save(valuation);
    }

    private void seedScore(Security security, DemoSecurity item) {
        if (scores.findTopBySecurityOrderByScoreDateDesc(security).isPresent()) {
            return;
        }
        ValueScore score = new ValueScore();
        score.setSecurity(security);
        score.setScoreDate(LocalDate.now());
        score.setMosScore(new BigDecimal("24.00"));
        score.setQualityScore(new BigDecimal("20.00"));
        score.setSafetyScore(new BigDecimal("16.00"));
        score.setGrowthScore(new BigDecimal("9.00"));
        score.setDividendScore(new BigDecimal("5.50"));
        score.setTotalScore(decimal(item.score()));
        scores.save(score);
    }

    private void seedRatio(Security security, DemoSecurity item) {
        if (ratios.findTopBySecurityOrderByReportDateDesc(security).isPresent()) {
            return;
        }
        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setSecurity(security);
        ratio.setPeriod(Period.TTM);
        ratio.setReportDate(LocalDate.now());
        ratio.setPeRatio(new BigDecimal("18.50"));
        ratio.setPbRatio(new BigDecimal("4.20"));
        ratio.setRoic(new BigDecimal("17.50"));
        ratio.setRoe(new BigDecimal("24.00"));
        ratio.setDebtToEquity(new BigDecimal("0.68"));
        ratio.setCurrentRatio(new BigDecimal("1.45"));
        ratio.setDividendYield(decimal(item.dividendYield()));
        ratio.setGrossMargin(new BigDecimal("58.00"));
        ratio.setOperatingMargin(new BigDecimal("29.00"));
        ratio.setNetMargin(new BigDecimal("22.00"));
        ratios.save(ratio);
    }

    private void seedFundamentals(Security security, DemoSecurity item) {
        seedFundamentalSnapshot(security, Period.ANNUAL);
        seedFundamentalSnapshot(security, Period.TTM);
    }

    private void seedFundamentalSnapshot(Security security, Period period) {
        if (fundamentals.findTopBySecurityAndPeriodOrderByReportDateDesc(security, period).isPresent()) {
            return;
        }
        FundamentalSnapshot snapshot = new FundamentalSnapshot();
        snapshot.setSecurity(security);
        snapshot.setPeriod(period);
        snapshot.setFiscalYear(LocalDate.now().getYear());
        snapshot.setReportDate(LocalDate.now());
        snapshot.setRevenue(new BigDecimal("100000000000.00"));
        snapshot.setNetIncome(new BigDecimal("18000000000.00"));
        snapshot.setOperatingIncome(new BigDecimal("24000000000.00"));
        snapshot.setGrossProfit(new BigDecimal("58000000000.00"));
        snapshot.setEps(new BigDecimal("6.40"));
        snapshot.setEpsDiluted(new BigDecimal("6.35"));
        snapshot.setFreeCashFlow(new BigDecimal("21000000000.00"));
        snapshot.setOperatingCashFlow(new BigDecimal("26000000000.00"));
        snapshot.setTotalAssets(new BigDecimal("250000000000.00"));
        snapshot.setTotalLiabilities(new BigDecimal("140000000000.00"));
        snapshot.setTotalEquity(new BigDecimal("110000000000.00"));
        snapshot.setTotalDebt(new BigDecimal("45000000000.00"));
        snapshot.setCash(new BigDecimal("30000000000.00"));
        snapshot.setSharesOutstanding(4_000_000_000L);
        fundamentals.save(snapshot);
    }

    private Watchlist seedWatchlist(User user) {
        return watchlists.findFirstByUser(user).orElseGet(() -> {
            Watchlist watchlist = new Watchlist();
            watchlist.setUser(user);
            watchlist.setName("Localstack candidates");
            return watchlists.save(watchlist);
        });
    }

    private void seedWatchlistItem(User user, Watchlist watchlist, String symbol) {
        if (watchlistItems.findBySymbolAndWatchlist_User(symbol, user).isPresent()) {
            return;
        }
        WatchlistItem item = new WatchlistItem();
        item.setWatchlist(watchlist);
        item.setSymbol(symbol);
        item.setMosAlertMin(new BigDecimal("12.00"));
        watchlistItems.save(item);
    }

    private void seedPortfolio(User user) {
        boolean exists = portfolios.findByUser(user).stream()
                .anyMatch(portfolio -> "Localstack Value Portfolio".equals(portfolio.getName()));
        if (exists) {
            return;
        }
        Portfolio portfolio = new Portfolio();
        portfolio.setUser(user);
        portfolio.setName("Localstack Value Portfolio");
        portfolio.setDescription("Seeded demo portfolio with priced holdings.");
        Portfolio saved = portfolios.save(portfolio);
        seedHolding(saved, "KO", "25", "54.00");
        seedHolding(saved, "JNJ", "8", "142.00");
        seedHolding(saved, "MSFT", "3", "390.00");
    }

    private void seedHolding(Portfolio portfolio, String symbol, String quantity, String costBasis) {
        Holding holding = new Holding();
        holding.setPortfolio(portfolio);
        holding.setSymbol(symbol);
        holding.setQuantity(decimal(quantity));
        holding.setAverageCostBasis(decimal(costBasis));
        holding.setCurrency("USD");
        holdings.save(holding);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private record DemoSecurity(String symbol, String companyName, String exchange, String sector,
                                String industry, String country, String currency, String marketCap,
                                String price, String fairValue, String marginOfSafety, String score,
                                String dividendYield) {
    }
}
