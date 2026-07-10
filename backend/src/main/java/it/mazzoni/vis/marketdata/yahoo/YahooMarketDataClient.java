package it.mazzoni.vis.marketdata.yahoo;

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
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.marketdata.MarketDataStatusTracker;
import it.mazzoni.vis.marketdata.fmp.dto.FmpDividendEntry;
import it.mazzoni.vis.marketdata.fmp.dto.FmpInsiderTradingEntry;
import it.mazzoni.vis.marketdata.fmp.dto.FmpStockListEntry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "market-data.source", havingValue = "yahoo")
public class YahooMarketDataClient implements MarketDataClient {

    private final YahooFinanceClient yahooFinanceClient;
    private final YahooFinanceAdapter adapter;
    private final MarketDataStatusTracker statusTracker;

    public YahooMarketDataClient(YahooFinanceClient yahooFinanceClient,
                                 YahooFinanceAdapter adapter,
                                 MarketDataStatusTracker statusTracker) {
        this.yahooFinanceClient = yahooFinanceClient;
        this.adapter = adapter;
        this.statusTracker = statusTracker;
    }

    @Override
    @Cacheable(cacheNames = "mdc-profile", key = "@cacheKeyHelper.key('profile', #symbol)")
    public CompanyProfile getProfile(String symbol) {
        try {
            QuoteSummaryResponse qsr = yahooFinanceClient.getQuoteSummary(symbol);
            ChartResponse cr = yahooFinanceClient.getChart(symbol);
            statusTracker.recordSuccess("yahoo");
            return adapter.toCompanyProfile(symbol, qsr, cr);
        } catch (SymbolNotFoundException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol, e);
        } catch (MarketDataUnavailableException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e);
        }
    }

    @Override
    @Cacheable(cacheNames = "mdc-fundamentals", key = "@cacheKeyHelper.key('fundamentals', #symbol)")
    public FundamentalSnapshot getFundamentals(String symbol) {
        try {
            QuoteSummaryResponse qsr = yahooFinanceClient.getQuoteSummary(symbol);
            ChartResponse cr = yahooFinanceClient.getChart(symbol);
            statusTracker.recordSuccess("yahoo");
            return adapter.toFundamentalSnapshot(symbol, qsr, cr);
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
            QuoteSummaryResponse qsr = yahooFinanceClient.getQuoteSummary(symbol);
            statusTracker.recordSuccess("yahoo");
            return adapter.toRatioSnapshot(symbol, qsr);
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
            ChartResponse cr = yahooFinanceClient.getChart(symbol);
            statusTracker.recordSuccess("yahoo");
            return adapter.toPriceQuote(symbol, cr);
        } catch (SymbolNotFoundException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol, e);
        } catch (MarketDataUnavailableException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e);
        }
    }

    @Override
    public List<FmpStockListEntry> listSymbols(String exchange) {
        throw new UnsupportedOperationException("listSymbols is not supported by the Yahoo Finance client");
    }

    @Override
    public List<HistoricalPriceQuote> getHistoricalPrices(String symbol, LocalDate from, LocalDate to) {
        throw new UnsupportedOperationException("getHistoricalPrices is not supported by the Yahoo Finance client");
    }

    @Override
    public List<FmpDividendEntry> getDividendHistory(String symbol) {
        throw new UnsupportedOperationException("getDividendHistory is not supported by the Yahoo Finance client");
    }

    @Override
    public List<FmpInsiderTradingEntry> getInsiderTransactions(String symbol) {
        throw new UnsupportedOperationException("getInsiderTransactions is not supported by the Yahoo Finance client");
    }

    @Override
    public Optional<BigDecimal> getFmpDcf(String symbol) {
        return Optional.empty();
    }
}
