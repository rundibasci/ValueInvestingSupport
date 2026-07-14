package it.mazzoni.vis.marketdata;

import it.mazzoni.vis.adapter.YahooFinanceAdapter;
import it.mazzoni.vis.client.yahoo.YahooFinanceClient;
import it.mazzoni.vis.client.yahoo.dto.ChartResponse;
import it.mazzoni.vis.client.yahoo.dto.QuoteSummaryResponse;
import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.HistoricalPriceQuote;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.exception.MarketDataUnavailableException;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.marketdata.fmp.dto.FmpDividendEntry;
import it.mazzoni.vis.marketdata.fmp.dto.FmpInsiderTradingEntry;
import it.mazzoni.vis.marketdata.fmp.dto.FmpStockListEntry;
import it.mazzoni.vis.observability.ObservabilitySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Primary MarketDataClient when source=fmp. Delegates to FMP; on PLAN_RESTRICTION (HTTP 402)
 * falls back transparently to Yahoo Finance for the four data-fetch methods.
 */
@Primary
@Service
@ConditionalOnProperty(name = "market-data.source", havingValue = "fmp")
public class FmpWithYahooFallbackMarketDataClient implements MarketDataClient {

    private static final Logger log = LoggerFactory.getLogger(FmpWithYahooFallbackMarketDataClient.class);

    private final MarketDataClient fmpClient;
    private final YahooFinanceClient yahooClient;
    private final YahooFinanceAdapter yahooAdapter;
    private final SourceTracker sourceTracker;
    private final MarketDataStatusTracker statusTracker;
    private final ObservabilitySupport observability;

    public FmpWithYahooFallbackMarketDataClient(
            @Qualifier("fmpMarketDataClient") MarketDataClient fmpClient,
            YahooFinanceClient yahooClient,
            YahooFinanceAdapter yahooAdapter,
            SourceTracker sourceTracker,
            MarketDataStatusTracker statusTracker,
            ObservabilitySupport observability) {
        this.fmpClient = fmpClient;
        this.yahooClient = yahooClient;
        this.yahooAdapter = yahooAdapter;
        this.sourceTracker = sourceTracker;
        this.statusTracker = statusTracker;
        this.observability = observability;
    }

    @Override
    @Cacheable(cacheNames = "mdc-profile", key = "@cacheKeyHelper.key('profile', #symbol)")
    public CompanyProfile getProfile(String symbol) {
        try {
            CompanyProfile result = fmpClient.getProfile(symbol);
            sourceTracker.record("FMP");
            statusTracker.recordSuccess("fmp");
            return enrichIncompleteProfile(symbol, result);
        } catch (MarketDataException e) {
            if (e.getErrorCode() != MarketDataException.ErrorCode.PLAN_RESTRICTION) throw e;
            recordFallback("profile", e);
        }
        CompanyProfile result = yahooProfile(symbol);
        sourceTracker.record("Yahoo");
        return result;
    }

    private CompanyProfile enrichIncompleteProfile(String symbol, CompanyProfile profile) {
        if (profile.exchange() != null && !profile.exchange().isBlank()) {
            return profile;
        }
        try {
            CompanyProfile yahoo = yahooProfile(symbol);
            if (yahoo.exchange() == null || yahoo.exchange().isBlank()) {
                return profile;
            }
            sourceTracker.record("Yahoo");
            return new CompanyProfile(
                    profile.symbol(), profile.companyName(), profile.sector(), profile.industry(),
                    profile.country(), profile.currency(), yahoo.exchange(), profile.marketCap(),
                    profile.description(), profile.website());
        } catch (MarketDataException e) {
            log.debug("Yahoo profile enrichment unavailable for {}: {}", symbol, e.getMessage());
            return profile;
        }
    }

    @Override
    @Cacheable(cacheNames = "mdc-fundamentals", key = "@cacheKeyHelper.key('fundamentals', #symbol)")
    public FundamentalSnapshot getFundamentals(String symbol) {
        try {
            FundamentalSnapshot result = fmpClient.getFundamentals(symbol);
            sourceTracker.record("FMP");
            statusTracker.recordSuccess("fmp");
            return result;
        } catch (MarketDataException e) {
            if (e.getErrorCode() != MarketDataException.ErrorCode.PLAN_RESTRICTION) throw e;
            recordFallback("fundamentals", e);
        }
        try {
            QuoteSummaryResponse qsr = yahooClient.getQuoteSummary(symbol);
            ChartResponse cr = yahooClient.getChart(symbol);
            FundamentalSnapshot result = yahooAdapter.toFundamentalSnapshot(symbol, qsr, cr);
            sourceTracker.record("Yahoo");
            statusTracker.recordFallback("PLAN_RESTRICTION");
            return result;
        } catch (SymbolNotFoundException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol, e);
        } catch (MarketDataUnavailableException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e);
        }
    }

    @Override
    @Cacheable(cacheNames = "mdc-ratios", key = "@cacheKeyHelper.key('ratios', #symbol)")
    public RatioSnapshot getRatios(String symbol) {
        try {
            RatioSnapshot result = fmpClient.getRatios(symbol);
            sourceTracker.record("FMP");
            statusTracker.recordSuccess("fmp");
            return result;
        } catch (MarketDataException e) {
            if (e.getErrorCode() != MarketDataException.ErrorCode.PLAN_RESTRICTION) throw e;
            recordFallback("ratios", e);
        }
        try {
            QuoteSummaryResponse qsr = yahooClient.getQuoteSummary(symbol);
            RatioSnapshot result = yahooAdapter.toRatioSnapshot(symbol, qsr);
            sourceTracker.record("Yahoo");
            statusTracker.recordFallback("PLAN_RESTRICTION");
            return result;
        } catch (SymbolNotFoundException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol, e);
        } catch (MarketDataUnavailableException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e);
        }
    }

    @Override
    @Cacheable(cacheNames = "mdc-annual-ratios", key = "@cacheKeyHelper.key('annual-ratios', #symbol)")
    public List<RatioSnapshot> getAnnualRatios(String symbol) {
        List<RatioSnapshot> result = fmpClient.getAnnualRatios(symbol);
        sourceTracker.record("FMP");
        statusTracker.recordSuccess("fmp");
        return result;
    }

    @Override
    @Cacheable(cacheNames = "mdc-quote", key = "@cacheKeyHelper.key('quote', #symbol)")
    public MarketPriceQuote getQuote(String symbol) {
        try {
            MarketPriceQuote result = fmpClient.getQuote(symbol);
            sourceTracker.record("FMP");
            statusTracker.recordSuccess("fmp");
            return enrichIncompleteQuote(symbol, result);
        } catch (MarketDataException e) {
            if (e.getErrorCode() != MarketDataException.ErrorCode.PLAN_RESTRICTION) throw e;
            recordFallback("quote", e);
        }
        try {
            ChartResponse cr = yahooClient.getChart(symbol);
            MarketPriceQuote result = yahooAdapter.toPriceQuote(symbol, cr);
            sourceTracker.record("Yahoo");
            statusTracker.recordFallback("PLAN_RESTRICTION");
            return result;
        } catch (SymbolNotFoundException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol, e);
        } catch (MarketDataUnavailableException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e);
        }
    }

    private MarketPriceQuote enrichIncompleteQuote(String symbol, MarketPriceQuote quote) {
        if (quote.volume() != null && quote.volume() > 0) {
            return quote;
        }
        try {
            MarketPriceQuote yahoo = yahooAdapter.toPriceQuote(symbol, yahooClient.getChart(symbol));
            if (yahoo.volume() == null || yahoo.volume() <= 0) {
                return quote;
            }
            sourceTracker.record("Yahoo");
            return new MarketPriceQuote(
                    quote.symbol(), quote.price(), quote.currency(), quote.change(),
                    quote.changePercent(), yahoo.volume());
        } catch (SymbolNotFoundException | MarketDataUnavailableException e) {
            log.debug("Yahoo quote enrichment unavailable for {}: {}", symbol, e.getMessage());
            return quote;
        }
    }

    @Override
    public List<FmpStockListEntry> listSymbols(String exchange) {
        return fmpClient.listSymbols(exchange);
    }

    @Override
    public List<HistoricalPriceQuote> getHistoricalPrices(String symbol, LocalDate from, LocalDate to) {
        List<HistoricalPriceQuote> result = fmpClient.getHistoricalPrices(symbol, from, to);
        statusTracker.recordSuccess("fmp");
        return result;
    }

    @Override
    public List<FmpDividendEntry> getDividendHistory(String symbol) {
        return fmpClient.getDividendHistory(symbol);
    }

    @Override
    public List<FmpInsiderTradingEntry> getInsiderTransactions(String symbol) {
        return fmpClient.getInsiderTransactions(symbol);
    }

    @Override
    public Optional<BigDecimal> getFmpDcf(String symbol) {
        return fmpClient.getFmpDcf(symbol);
    }

    private CompanyProfile yahooProfile(String symbol) {
        try {
            QuoteSummaryResponse qsr = yahooClient.getQuoteSummary(symbol);
            ChartResponse cr = yahooClient.getChart(symbol);
            statusTracker.recordFallback("PLAN_RESTRICTION");
            return yahooAdapter.toCompanyProfile(symbol, qsr, cr);
        } catch (SymbolNotFoundException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol, e);
        } catch (MarketDataUnavailableException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e);
        }
    }

    private void recordFallback(String operation, MarketDataException e) {
        String reason = e.getErrorCode().name();
        log.info("market_data_fallback provider=fmp fallbackProvider=yahoo operation={} reason={}", operation, reason);
        observability.count("vis.marketdata.fallback",
                observability.tags("provider", "fmp", "operation", operation, "fallback", "yahoo", "error", reason));
        statusTracker.recordFallback(reason);
    }
}
