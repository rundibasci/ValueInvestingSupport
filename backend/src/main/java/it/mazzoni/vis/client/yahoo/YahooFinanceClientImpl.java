package it.mazzoni.vis.client.yahoo;

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

    public YahooFinanceClientImpl(WebClient yahooFinanceWebClient) {
        this.webClient = yahooFinanceWebClient;
    }

    @Override
    @Cacheable(key = "'qs:' + #symbol.toUpperCase()")
    public QuoteSummaryResponse getQuoteSummary(String symbol) {
        try {
            QuoteSummaryResponse response = webClient.get()
                    .uri("/v10/finance/quoteSummary/" + symbol.toUpperCase() + "?modules=" + MODULES)
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
        } catch (SymbolNotFoundException | MarketDataUnavailableException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().is5xxServerError()) {
                throw new MarketDataUnavailableException(
                        "Yahoo Finance server error: " + e.getStatusCode(), e);
            }
            throw new SymbolNotFoundException(symbol);
        } catch (WebClientRequestException e) {
            throw new MarketDataUnavailableException(
                    "Yahoo Finance is unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    @Cacheable(key = "'ch:' + #symbol.toUpperCase()")
    public ChartResponse getChart(String symbol) {
        try {
            ChartResponse response = webClient.get()
                    .uri("/v8/finance/chart/" + symbol.toUpperCase())
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
        } catch (SymbolNotFoundException | MarketDataUnavailableException e) {
            throw e;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().is5xxServerError()) {
                throw new MarketDataUnavailableException(
                        "Yahoo Finance server error: " + e.getStatusCode(), e);
            }
            throw new SymbolNotFoundException(symbol);
        } catch (WebClientRequestException e) {
            throw new MarketDataUnavailableException(
                    "Yahoo Finance is unavailable: " + e.getMessage(), e);
        }
    }
}
