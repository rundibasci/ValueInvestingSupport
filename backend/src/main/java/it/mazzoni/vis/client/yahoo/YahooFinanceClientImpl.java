package it.mazzoni.vis.client.yahoo;

import it.mazzoni.vis.client.yahoo.YahooCrumbProvider.CrumbSession;
import it.mazzoni.vis.client.yahoo.dto.ChartResponse;
import it.mazzoni.vis.client.yahoo.dto.QuoteSummaryResponse;
import it.mazzoni.vis.exception.MarketDataUnavailableException;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Service
@CacheConfig(cacheNames = "yahoo-finance")
public class YahooFinanceClientImpl implements YahooFinanceClient {

    private static final String MODULES =
            "financialData,defaultKeyStatistics," +
            "incomeStatementHistory,balanceSheetHistory," +
            "cashflowStatementHistory,summaryDetail,assetProfile";

    private final WebClient webClient;
    private final YahooCrumbProvider crumbProvider;

    public YahooFinanceClientImpl(WebClient yahooFinanceWebClient,
                                  YahooCrumbProvider crumbProvider) {
        this.webClient = yahooFinanceWebClient;
        this.crumbProvider = crumbProvider;
    }

    @Override
    @Cacheable(key = "'qs:' + #symbol.toUpperCase()")
    public QuoteSummaryResponse getQuoteSummary(String symbol) {
        CrumbSession session = crumbProvider.acquireSession();
        try {
            return doGetQuoteSummary(symbol, session);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                crumbProvider.invalidate();
                try {
                    return doGetQuoteSummary(symbol, crumbProvider.acquireSession());
                } catch (WebClientResponseException e2) {
                    throw toAppException(symbol, e2);
                }
            }
            throw toAppException(symbol, e);
        } catch (WebClientRequestException e) {
            throw new MarketDataUnavailableException(
                    "Yahoo Finance is unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    @Cacheable(key = "'ch:' + #symbol.toUpperCase()")
    public ChartResponse getChart(String symbol) {
        CrumbSession session = crumbProvider.acquireSession();
        try {
            return doGetChart(symbol, session);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                crumbProvider.invalidate();
                try {
                    return doGetChart(symbol, crumbProvider.acquireSession());
                } catch (WebClientResponseException e2) {
                    throw toAppException(symbol, e2);
                }
            }
            throw toAppException(symbol, e);
        } catch (WebClientRequestException e) {
            throw new MarketDataUnavailableException(
                    "Yahoo Finance is unavailable: " + e.getMessage(), e);
        }
    }

    private QuoteSummaryResponse doGetQuoteSummary(String symbol, CrumbSession session) {
        QuoteSummaryResponse response = webClient.get()
                .uri("/v10/finance/quoteSummary/" + symbol.toUpperCase()
                     + "?modules=" + MODULES + "&crumb=" + session.crumb())
                .header("Cookie", session.cookie())
                .retrieve()
                .bodyToMono(QuoteSummaryResponse.class)
                .block(Duration.ofSeconds(10));

        if (response == null
                || response.quoteSummary() == null
                || response.quoteSummary().error() != null
                || response.quoteSummary().result() == null
                || response.quoteSummary().result().isEmpty()) {
            throw new SymbolNotFoundException(symbol);
        }
        return response;
    }

    private ChartResponse doGetChart(String symbol, CrumbSession session) {
        ChartResponse response = webClient.get()
                .uri("/v8/finance/chart/" + symbol.toUpperCase()
                     + "?crumb=" + session.crumb())
                .header("Cookie", session.cookie())
                .retrieve()
                .bodyToMono(ChartResponse.class)
                .block(Duration.ofSeconds(10));

        if (response == null
                || response.chart() == null
                || response.chart().error() != null
                || response.chart().result() == null
                || response.chart().result().isEmpty()) {
            throw new SymbolNotFoundException(symbol);
        }
        return response;
    }

    private RuntimeException toAppException(String symbol, WebClientResponseException e) {
        if (e.getStatusCode().is5xxServerError() || e.getStatusCode().value() == 429) {
            return new MarketDataUnavailableException(
                    "Yahoo Finance unavailable: " + e.getStatusCode(), e);
        }
        return new SymbolNotFoundException(symbol);
    }
}
