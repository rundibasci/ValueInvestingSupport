package it.mazzoni.vis.marketdata.yahoo;

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
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "market-data.source", havingValue = "yahoo")
public class YahooMarketDataClient implements MarketDataClient {

    private final YahooFinanceClient yahooFinanceClient;
    private final YahooFinanceAdapter adapter;

    public YahooMarketDataClient(YahooFinanceClient yahooFinanceClient,
                                 YahooFinanceAdapter adapter) {
        this.yahooFinanceClient = yahooFinanceClient;
        this.adapter = adapter;
    }

    @Override
    @Cacheable(cacheNames = "mdc-profile", key = "@cacheKeyHelper.key('profile', #symbol)")
    public CompanyProfile getProfile(String symbol) {
        try {
            QuoteSummaryResponse qsr = yahooFinanceClient.getQuoteSummary(symbol);
            ChartResponse cr = yahooFinanceClient.getChart(symbol);
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
            return adapter.toPriceQuote(symbol, cr);
        } catch (SymbolNotFoundException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol, e);
        } catch (MarketDataUnavailableException e) {
            throw new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e);
        }
    }
}
