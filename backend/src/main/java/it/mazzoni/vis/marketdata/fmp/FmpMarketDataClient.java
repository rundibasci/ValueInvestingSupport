package it.mazzoni.vis.marketdata.fmp;

import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.HistoricalPriceQuote;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.marketdata.fmp.dto.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "market-data.source", havingValue = "fmp")
public class FmpMarketDataClient implements MarketDataClient {

    private final WebClient fmpWebClient;
    private final FmpAdapter adapter;

    private static final Retry RETRY_SPEC = Retry
            .backoff(3, Duration.ofSeconds(1))
            .filter(FmpMarketDataClient::isRetryable)
            .onRetryExhaustedThrow((spec, signal) -> signal.failure());

    public FmpMarketDataClient(WebClient fmpWebClient, FmpAdapter adapter) {
        this.fmpWebClient = fmpWebClient;
        this.adapter = adapter;
    }

    @Override
    @Cacheable(cacheNames = "mdc-profile", key = "@cacheKeyHelper.key('profile', #symbol)")
    public CompanyProfile getProfile(String symbol) {
        return fmpWebClient.get()
                .uri(u -> u.path("/profile").queryParam("symbol", symbol).build())
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        resp -> Mono.error(new MarketDataException(
                                MarketDataException.ErrorCode.NOT_FOUND, symbol)))
                .onStatus(status -> status.value() == 402,
                        resp -> Mono.error(new MarketDataException(
                                MarketDataException.ErrorCode.PLAN_RESTRICTION, symbol)))
                .onStatus(status -> status.is5xxServerError() || status.value() == 429,
                        resp -> Mono.error(new MarketDataException(
                                MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol)))
                .bodyToFlux(FmpProfileEntry.class)
                .retryWhen(RETRY_SPEC)
                .next()
                .switchIfEmpty(Mono.error(new MarketDataException(
                        MarketDataException.ErrorCode.NOT_FOUND, symbol)))
                .map(adapter::toCompanyProfile)
                .onErrorMap(WebClientResponseException.class,
                        e -> new MarketDataException(
                                MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e))
                .block();
    }

    @Override
    @Cacheable(cacheNames = "mdc-fundamentals", key = "@cacheKeyHelper.key('fundamentals', #symbol)")
    public FundamentalSnapshot getFundamentals(String symbol) {
        List<FmpIncomeStatementEntry> income = fetchList(
                "/income-statement", symbol, 11, new ParameterizedTypeReference<>() {});
        List<FmpBalanceSheetEntry> balance = fetchList(
                "/balance-sheet-statement", symbol, 11, new ParameterizedTypeReference<>() {});
        List<FmpCashFlowEntry> cashflow = fetchList(
                "/cash-flow-statement", symbol, 11, new ParameterizedTypeReference<>() {});

        if (income.isEmpty() && balance.isEmpty() && cashflow.isEmpty()) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol);
        }

        FmpProfileEntry profile = null;
        try {
            List<FmpProfileEntry> profiles = fetchList(
                    "/profile", symbol, new ParameterizedTypeReference<>() {});
            profile = profiles.isEmpty() ? null : profiles.get(0);
        } catch (MarketDataException ignored) {}

        java.math.BigDecimal currentPrice = profile != null ? profile.price() : null;
        return adapter.toFundamentalSnapshot(symbol, income, balance, cashflow, profile, currentPrice);
    }

    @Override
    @Cacheable(cacheNames = "mdc-ratios", key = "@cacheKeyHelper.key('ratios', #symbol)")
    public RatioSnapshot getRatios(String symbol) {
        List<FmpRatiosEntry> ratios = fetchList(
                "/ratios", symbol, new ParameterizedTypeReference<>() {});
        if (ratios.isEmpty()) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol);
        }
        FmpKeyMetricsEntry metrics = null;
        try {
            List<FmpKeyMetricsEntry> keyMetrics = fetchList(
                    "/key-metrics", symbol, new ParameterizedTypeReference<>() {});
            metrics = keyMetrics.isEmpty() ? null : keyMetrics.get(0);
        } catch (MarketDataException ignored) {
            // Ratios remain useful when key metrics are unavailable on the active FMP plan.
        }
        return adapter.toRatioSnapshot(symbol, ratios.get(0), metrics);
    }

    @Override
    @Cacheable(cacheNames = "mdc-annual-ratios", key = "@cacheKeyHelper.key('annual-ratios', #symbol)")
    public List<RatioSnapshot> getAnnualRatios(String symbol) {
        List<FmpRatiosEntry> ratios = fetchList(
                "/ratios", symbol, 11, new ParameterizedTypeReference<>() {});
        if (ratios.isEmpty()) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol);
        }
        List<FmpKeyMetricsEntry> fetchedKeyMetrics;
        try {
            fetchedKeyMetrics = fetchList("/key-metrics", symbol, 11, new ParameterizedTypeReference<>() {});
        } catch (MarketDataException ignored) {
            fetchedKeyMetrics = List.of();
        }
        List<FmpKeyMetricsEntry> keyMetrics = fetchedKeyMetrics;
        return ratios.stream()
                .limit(11)
                .map(ratio -> adapter.toRatioSnapshot(symbol, ratio, matchingMetric(ratio, keyMetrics)))
                .toList();
    }

    @Override
    @Cacheable(cacheNames = "mdc-quote", key = "@cacheKeyHelper.key('quote', #symbol)")
    public MarketPriceQuote getQuote(String symbol) {
        List<FmpQuoteEntry> quotes = fetchList(
                "/quote", symbol, new ParameterizedTypeReference<>() {});
        if (quotes.isEmpty()) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol);
        }
        return adapter.toMarketPriceQuote(symbol, quotes.get(0));
    }

    @Override
    public List<FmpStockListEntry> listSymbols(String exchange) {
        // /stock-list no longer returns exchange/type/market-cap fields on the current FMP
        // stable API; /company-screener does, and supports server-side exchange filtering.
        List<FmpStockListEntry> all = fmpWebClient.get()
                .uri(u -> u.path("/company-screener")
                        .queryParam("exchange", exchange)
                        .queryParam("limit", 5000)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<FmpStockListEntry>>() {})
                .retryWhen(RETRY_SPEC)
                .onErrorMap(WebClientResponseException.class,
                        e -> new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, exchange, e))
                .block();
        if (all == null) return List.of();
        return all.stream()
                .filter(e -> exchange.equalsIgnoreCase(e.exchangeShortName()))
                .filter(e -> !Boolean.TRUE.equals(e.isEtf()) && !Boolean.TRUE.equals(e.isFund()))
                .toList();
    }

    @Override
    public List<HistoricalPriceQuote> getHistoricalPrices(String symbol, LocalDate from, LocalDate to) {
        List<FmpHistoricalPriceEntry> rows = fmpWebClient.get()
                .uri(u -> u.path("/historical-price-eod/full")
                        .queryParam("symbol", symbol.toUpperCase())
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .build())
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        resp -> Mono.error(new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol)))
                .onStatus(status -> status.value() == 402,
                        resp -> Mono.error(new MarketDataException(MarketDataException.ErrorCode.PLAN_RESTRICTION, symbol)))
                .bodyToMono(new ParameterizedTypeReference<List<FmpHistoricalPriceEntry>>() {})
                .retryWhen(RETRY_SPEC)
                .onErrorMap(WebClientResponseException.class,
                        e -> new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e))
                .block();
        if (rows == null) return List.of();
        return rows.stream()
                .filter(row -> row.date() != null && row.close() != null)
                .map(row -> new HistoricalPriceQuote(symbol.toUpperCase(), row.date(), row.close(), row.volume()))
                .toList();
    }

    @Override
    public List<FmpDividendEntry> getDividendHistory(String symbol) {
        List<FmpDividendEntry> result = fmpWebClient.get()
                .uri(u -> u.path("/dividends")
                        .queryParam("symbol", symbol.toUpperCase())
                        .build())
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        resp -> Mono.error(new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol)))
                .onStatus(status -> status.value() == 402,
                        resp -> Mono.error(new MarketDataException(MarketDataException.ErrorCode.PLAN_RESTRICTION, symbol)))
                .bodyToMono(new ParameterizedTypeReference<List<FmpDividendEntry>>() {})
                .retryWhen(RETRY_SPEC)
                .onErrorMap(WebClientResponseException.class,
                        e -> new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e))
                .block();
        return result != null ? result : List.of();
    }

    @Override
    public List<FmpInsiderTradingEntry> getInsiderTransactions(String symbol) {
        List<FmpInsiderTradingEntry> result = fmpWebClient.get()
                .uri(u -> u.path("/insider-trading/search")
                        .queryParam("symbol", symbol.toUpperCase())
                        .queryParam("page", 0)
                        .queryParam("limit", 50)
                        .build())
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        resp -> Mono.error(new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol)))
                .bodyToMono(new ParameterizedTypeReference<List<FmpInsiderTradingEntry>>() {})
                .retryWhen(RETRY_SPEC)
                .onErrorMap(WebClientResponseException.class,
                        e -> new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e))
                .block();
        return result != null ? result : List.of();
    }

    @Override
    public Optional<BigDecimal> getFmpDcf(String symbol) {
        List<FmpDcfEntry> entries = fmpWebClient.get()
                .uri(u -> u.path("/discounted-cash-flow/{symbol}").build(symbol.toUpperCase()))
                .retrieve()
                .onStatus(status -> status.value() == 404, resp -> Mono.empty())
                .bodyToMono(new ParameterizedTypeReference<List<FmpDcfEntry>>() {})
                .retryWhen(RETRY_SPEC)
                .onErrorResume(WebClientResponseException.class, e -> Mono.empty())
                .block();
        if (entries == null || entries.isEmpty()) return Optional.empty();
        return Optional.ofNullable(entries.get(0).dcf());
    }

    private <T> List<T> fetchList(String path, String symbol,
                                   ParameterizedTypeReference<List<T>> type) {
        return fetchList(path, symbol, 5, type);
    }

    private <T> List<T> fetchList(String path, String symbol, int limit,
                                   ParameterizedTypeReference<List<T>> type) {
        return fmpWebClient.get()
                .uri(u -> u.path(path).queryParam("symbol", symbol).queryParam("limit", limit).build())
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        resp -> Mono.error(new MarketDataException(
                                MarketDataException.ErrorCode.NOT_FOUND, symbol)))
                .onStatus(status -> status.value() == 402,
                        resp -> Mono.error(new MarketDataException(
                                MarketDataException.ErrorCode.PLAN_RESTRICTION, symbol)))
                .onStatus(status -> status.is5xxServerError() || status.value() == 429,
                        resp -> Mono.error(new MarketDataException(
                                MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol)))
                .bodyToMono(type)
                .retryWhen(RETRY_SPEC)
                .onErrorMap(WebClientResponseException.class,
                        e -> new MarketDataException(
                                MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, symbol, e))
                .block();
    }

    private FmpKeyMetricsEntry matchingMetric(FmpRatiosEntry ratio, List<FmpKeyMetricsEntry> keyMetrics) {
        if (ratio.date() == null) return null;
        return keyMetrics.stream()
                .filter(metric -> ratio.date().equals(metric.date()))
                .findFirst()
                .orElse(null);
    }

    private static boolean isRetryable(Throwable t) {
        return t instanceof MarketDataException mde
                && mde.getErrorCode() == MarketDataException.ErrorCode.SERVICE_UNAVAILABLE;
    }
}
