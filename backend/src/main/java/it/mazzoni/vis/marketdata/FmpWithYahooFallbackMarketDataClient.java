package it.mazzoni.vis.marketdata;

import it.mazzoni.vis.adapter.YahooFinanceAdapter;
import it.mazzoni.vis.client.yahoo.YahooFinanceClient;
import it.mazzoni.vis.client.yahoo.dto.ChartResponse;
import it.mazzoni.vis.client.yahoo.dto.QuoteSummaryResponse;
import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.exception.MarketDataUnavailableException;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.marketdata.fmp.dto.FmpDividendEntry;
import it.mazzoni.vis.marketdata.fmp.dto.FmpInsiderTradingEntry;
import it.mazzoni.vis.marketdata.fmp.dto.FmpStockListEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    public FmpWithYahooFallbackMarketDataClient(
            @Qualifier("fmpMarketDataClient") MarketDataClient fmpClient,
            YahooFinanceClient yahooClient,
            YahooFinanceAdapter yahooAdapter,
            SourceTracker sourceTracker) {
        this.fmpClient = fmpClient;
        this.yahooClient = yahooClient;
        this.yahooAdapter = yahooAdapter;
        this.sourceTracker = sourceTracker;
    }

    @Override
    @Cacheable(cacheNames = "mdc-profile", key = "@cacheKeyHelper.key('profile', #symbol)")
    public CompanyProfile getProfile(String symbol) {
        try {
            CompanyProfile result = fmpClient.getProfile(symbol);
            sourceTracker.record("FMP");
            return result;
        } catch (MarketDataException e) {
            if (e.getErrorCode() != MarketDataException.ErrorCode.PLAN_RESTRICTION) throw e;
            log.info("FMP plan restriction for profile [{}], falling back to Yahoo", symbol);
        }
        CompanyProfile result = yahooProfile(symbol);
        sourceTracker.record("Yahoo");
        return result;
    }

    @Override
    @Cacheable(cacheNames = "mdc-fundamentals", key = "@cacheKeyHelper.key('fundamentals', #symbol)")
    public FundamentalSnapshot getFundamentals(String symbol) {
        try {
            FundamentalSnapshot result = fmpClient.getFundamentals(symbol);
            sourceTracker.record("FMP");
            return result;
        } catch (MarketDataException e) {
            if (e.getErrorCode() != MarketDataException.ErrorCode.PLAN_RESTRICTION) throw e;
            log.info("FMP plan restriction for fundamentals [{}], falling back to Yahoo", symbol);
        }
        try {
            QuoteSummaryResponse qsr = yahooClient.getQuoteSummary(symbol);
            ChartResponse cr = yahooClient.getChart(symbol);
            FundamentalSnapshot result = yahooAdapter.toFundamentalSnapshot(symbol, qsr, cr);
            sourceTracker.record("Yahoo");
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
            return result;
        } catch (MarketDataException e) {
            if (e.getErrorCode() != MarketDataException.ErrorCode.PLAN_RESTRICTION) throw e;
            log.info("FMP plan restriction for ratios [{}], falling back to Yahoo", symbol);
        }
        try {
            QuoteSummaryResponse qsr = yahooClient.getQuoteSummary(symbol);
            RatioSnapshot result = yahooAdapter.toRatioSnapshot(symbol, qsr);
            sourceTracker.record("Yahoo");
            return result;
        } catch (SymbolNotFoundException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol, e);
        } catch (MarketDataUnavailableException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e);
        }
    }

    @Override
    @Cacheable(cacheNames = "mdc-quote", key = "@cacheKeyHelper.key('quote', #symbol)")
    public MarketPriceQuote getQuote(String symbol) {
        try {
            MarketPriceQuote result = fmpClient.getQuote(symbol);
            sourceTracker.record("FMP");
            return result;
        } catch (MarketDataException e) {
            if (e.getErrorCode() != MarketDataException.ErrorCode.PLAN_RESTRICTION) throw e;
            log.info("FMP plan restriction for quote [{}], falling back to Yahoo", symbol);
        }
        try {
            ChartResponse cr = yahooClient.getChart(symbol);
            MarketPriceQuote result = yahooAdapter.toPriceQuote(symbol, cr);
            sourceTracker.record("Yahoo");
            return result;
        } catch (SymbolNotFoundException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol, e);
        } catch (MarketDataUnavailableException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e);
        }
    }

    @Override
    public List<FmpStockListEntry> listSymbols(String exchange) {
        return fmpClient.listSymbols(exchange);
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
            return yahooAdapter.toCompanyProfile(symbol, qsr, cr);
        } catch (SymbolNotFoundException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol, e);
        } catch (MarketDataUnavailableException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e);
        }
    }
}
