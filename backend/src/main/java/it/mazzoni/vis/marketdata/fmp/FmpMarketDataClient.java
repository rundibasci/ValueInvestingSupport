package it.mazzoni.vis.marketdata.fmp;

import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.marketdata.fmp.dto.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

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
    public CompanyProfile getProfile(String symbol) {
        return fmpWebClient.get()
                .uri(u -> u.path("/profile").queryParam("symbol", symbol).build())
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        resp -> Mono.error(new MarketDataException(
                                MarketDataException.ErrorCode.NOT_FOUND, symbol)))
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
    public FundamentalSnapshot getFundamentals(String symbol) {
        List<FmpIncomeStatementEntry> income = fetchList(
                "/income-statement", symbol, new ParameterizedTypeReference<>() {});
        List<FmpBalanceSheetEntry> balance = fetchList(
                "/balance-sheet-statement", symbol, new ParameterizedTypeReference<>() {});
        List<FmpCashFlowEntry> cashflow = fetchList(
                "/cash-flow-statement", symbol, new ParameterizedTypeReference<>() {});

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
    public RatioSnapshot getRatios(String symbol) {
        List<FmpRatiosEntry> ratios = fetchList(
                "/ratios", symbol, new ParameterizedTypeReference<>() {});
        if (ratios.isEmpty()) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol);
        }
        return adapter.toRatioSnapshot(symbol, ratios.get(0));
    }

    @Override
    public MarketPriceQuote getQuote(String symbol) {
        List<FmpQuoteEntry> quotes = fetchList(
                "/quote", symbol, new ParameterizedTypeReference<>() {});
        if (quotes.isEmpty()) {
            throw new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, symbol);
        }
        return adapter.toMarketPriceQuote(symbol, quotes.get(0));
    }

    private <T> List<T> fetchList(String path, String symbol,
                                   ParameterizedTypeReference<List<T>> type) {
        return fmpWebClient.get()
                .uri(u -> u.path(path).queryParam("symbol", symbol).queryParam("limit", 5).build())
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        resp -> Mono.error(new MarketDataException(
                                MarketDataException.ErrorCode.NOT_FOUND, symbol)))
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

    private static boolean isRetryable(Throwable t) {
        return t instanceof MarketDataException mde
                && mde.getErrorCode() == MarketDataException.ErrorCode.SERVICE_UNAVAILABLE;
    }
}
