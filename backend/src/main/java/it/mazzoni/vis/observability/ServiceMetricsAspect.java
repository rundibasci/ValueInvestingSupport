package it.mazzoni.vis.observability;

import io.micrometer.core.instrument.Tag;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ServiceMetricsAspect {

    private final ObservabilitySupport observability;

    public ServiceMetricsAspect(ObservabilitySupport observability) {
        this.observability = observability;
    }

    @Around("execution(public * it.mazzoni.vis.screener.ScreenerService.*(..))")
    public Object timeScreener(ProceedingJoinPoint joinPoint) throws Throwable {
        return timeComponent(joinPoint, "screener", "vis.screener.latency");
    }

    @Around("execution(public * it.mazzoni.vis.security.SecurityReviewService.*(..))")
    public Object timeSecurityReview(ProceedingJoinPoint joinPoint) throws Throwable {
        return timeComponent(joinPoint, "security-review", "vis.security.review.latency");
    }

    @Around("execution(public * it.mazzoni.vis.valuation.ValuationService.*(..))")
    public Object timeValuation(ProceedingJoinPoint joinPoint) throws Throwable {
        return timeComponent(joinPoint, "valuation", "vis.valuation.latency");
    }

    @Around("execution(public * it.mazzoni.vis.scoring.ValueScoreService.*(..)) || execution(public * it.mazzoni.vis.scoring.ScoreService.*(..))")
    public Object timeScoring(ProceedingJoinPoint joinPoint) throws Throwable {
        return timeComponent(joinPoint, "scoring", "vis.scoring.latency");
    }

    @Around("execution(public * it.mazzoni.vis.portfolio.*Service.*(..))")
    public Object timePortfolio(ProceedingJoinPoint joinPoint) throws Throwable {
        return timeComponent(joinPoint, "portfolio", "vis.portfolio.latency");
    }

    @Around("execution(public * it.mazzoni.vis.watchlist.WatchlistService.*(..))")
    public Object timeWatchlist(ProceedingJoinPoint joinPoint) throws Throwable {
        return timeComponent(joinPoint, "watchlist", "vis.watchlist.latency");
    }

    private Object timeComponent(ProceedingJoinPoint joinPoint, String component, String metricName) throws Throwable {
        return observability.timeThrowing(metricName,
                observability.tags("component", component, "operation", joinPoint.getSignature().getName()),
                joinPoint::proceed);
    }
}
