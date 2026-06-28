package it.mazzoni.vis.observability;

import it.mazzoni.vis.marketdata.MarketDataException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MarketDataMetricsAspect {

    private final ObservabilitySupport observability;

    public MarketDataMetricsAspect(ObservabilitySupport observability) {
        this.observability = observability;
    }

    @Around("execution(public * it.mazzoni.vis.marketdata.fmp.FmpMarketDataClient.*(..))")
    public Object timeFmp(ProceedingJoinPoint joinPoint) throws Throwable {
        return time(joinPoint, "fmp", "false");
    }

    @Around("execution(public * it.mazzoni.vis.marketdata.yahoo.YahooMarketDataClient.*(..))")
    public Object timeYahoo(ProceedingJoinPoint joinPoint) throws Throwable {
        return time(joinPoint, "yahoo", "false");
    }

    private Object time(ProceedingJoinPoint joinPoint, String provider, String fallback) throws Throwable {
        try {
            return observability.timeThrowing("vis.marketdata.client.latency",
                    observability.tags(
                            "provider", provider,
                            "operation", operation(joinPoint.getSignature().getName()),
                            "fallback", fallback),
                    joinPoint::proceed);
        } catch (MarketDataException e) {
            observability.count("vis.marketdata.client.error",
                    observability.tags(
                            "provider", provider,
                            "operation", operation(joinPoint.getSignature().getName()),
                            "fallback", fallback,
                            "error", e.getErrorCode().name()));
            throw e;
        } catch (Exception e) {
            throw e;
        }
    }

    private String operation(String methodName) {
        return switch (methodName) {
            case "getProfile" -> "profile";
            case "getFundamentals" -> "fundamentals";
            case "getRatios" -> "ratios";
            case "getQuote" -> "quote";
            case "listSymbols" -> "stock-list";
            case "getDividendHistory" -> "dividends";
            case "getInsiderTransactions" -> "insiders";
            case "getFmpDcf" -> "dcf";
            default -> methodName;
        };
    }
}
