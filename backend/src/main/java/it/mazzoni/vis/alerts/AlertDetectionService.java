package it.mazzoni.vis.alerts;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Evaluates persisted data only; it never performs live market-data calls. */
@Service
public class AlertDetectionService {
    private static final BigDecimal PRICE_MOVEMENT_PERCENT = BigDecimal.valueOf(5);

    private final WatchlistItemRepository watchlistItems;
    private final HoldingRepository holdings;
    private final SecurityRepository securities;
    private final ValuationResultRepository valuations;
    private final ValueScoreRepository scores;
    private final PriceQuoteRepository quotes;
    private final DividendRecordRepository dividends;
    private final InsiderTradeRepository insiderTrades;
    private final FundamentalSnapshotRepository fundamentals;
    private final RebalanceProposalRepository rebalanceProposals;
    private final AlertRepository alerts;
    private final AlertDeliveryService alertDeliveryService;

    public AlertDetectionService(WatchlistItemRepository watchlistItems, HoldingRepository holdings,
                                 SecurityRepository securities, ValuationResultRepository valuations,
                                 ValueScoreRepository scores, PriceQuoteRepository quotes,
                                 DividendRecordRepository dividends, InsiderTradeRepository insiderTrades,
                                 FundamentalSnapshotRepository fundamentals,
                                 RebalanceProposalRepository rebalanceProposals, AlertRepository alerts,
                                 AlertDeliveryService alertDeliveryService) {
        this.watchlistItems = watchlistItems; this.holdings = holdings; this.securities = securities;
        this.valuations = valuations; this.scores = scores; this.quotes = quotes; this.dividends = dividends;
        this.insiderTrades = insiderTrades; this.fundamentals = fundamentals;
        this.rebalanceProposals = rebalanceProposals; this.alerts = alerts;
        this.alertDeliveryService = alertDeliveryService;
    }

    @Transactional
    public int execute() {
        int created = 0;
        for (WatchlistItem item : watchlistItems.findAll()) {
            try { created += evaluateWatchlistItem(item); } catch (RuntimeException ignored) { /* isolate a bad item */ }
        }
        for (Holding holding : holdings.findAll()) {
            try { created += evaluateHolding(holding); } catch (RuntimeException ignored) { /* isolate a bad holding */ }
        }
        return created;
    }

    private int evaluateWatchlistItem(WatchlistItem item) {
        User user = item.getWatchlist().getUser();
        String symbol = item.getSymbol().toUpperCase();
        Security security = securities.findBySymbol(symbol).orElse(null);
        if (security == null) return 0;
        int created = 0;
        Optional<ValuationResult> valuation = valuations.findTopBySecurityOrderByValuationDateDesc(security);
        if (item.getMosAlertMin() != null && valuation.map(ValuationResult::getMarginOfSafety)
                .filter(value -> value.compareTo(item.getMosAlertMin()) >= 0).isPresent())
            created += persist(user, symbol, AlertType.MOS_ENTRY, item.getMosAlertMin());
        if (item.getMosAlertMax() != null && valuation.map(ValuationResult::getMarginOfSafety)
                .filter(value -> value.compareTo(item.getMosAlertMax()) <= 0).isPresent())
            created += persist(user, symbol, AlertType.MOS_EXIT, item.getMosAlertMax());
        if (item.getFundamentalDegradeThreshold() != null && scores.findTopBySecurityOrderByScoreDateDesc(security)
                .map(ValueScore::getTotalScore).filter(value -> value.compareTo(item.getFundamentalDegradeThreshold()) < 0).isPresent())
            created += persist(user, symbol, AlertType.FUNDAMENTAL_DEGRADE, item.getFundamentalDegradeThreshold());
        return created + commonRules(user, symbol, security);
    }

    private int evaluateHolding(Holding holding) {
        Security security = securities.findBySymbol(holding.getSymbol().toUpperCase()).orElse(null);
        if (security == null) return 0;
        return commonRules(holding.getPortfolio().getUser(), holding.getSymbol().toUpperCase(), security)
                + rebalanceRule(holding.getPortfolio().getUser(), holding.getSymbol().toUpperCase());
    }

    private int commonRules(User user, String symbol, Security security) {
        int created = 0;
        List<PriceQuote> priceHistory = quotes.findTop2BySecurityOrderByQuoteDateDesc(security);
        if (priceHistory.size() == 2 && priceHistory.get(1).getClose().signum() > 0) {
            BigDecimal change = priceHistory.get(0).getClose().subtract(priceHistory.get(1).getClose()).abs()
                    .multiply(BigDecimal.valueOf(100)).divide(priceHistory.get(1).getClose(), 4, RoundingMode.HALF_UP);
            if (change.compareTo(PRICE_MOVEMENT_PERCENT) >= 0) created += persist(user, symbol, AlertType.PRICE_TARGET_HIT, PRICE_MOVEMENT_PERCENT);
        }
        List<DividendRecord> dividendHistory = dividends.findBySecurityOrderByExDividendDateDesc(security);
        if (dividendHistory.size() >= 2 && dividendHistory.get(0).getAmount().compareTo(dividendHistory.get(1).getAmount()) < 0)
            created += persist(user, symbol, AlertType.DIVIDEND_CUT, dividendHistory.get(1).getAmount());
        if (insiderTrades.findBySecurityOrderByTradeDateDesc(security).stream().findFirst()
                .map(trade -> trade.getTransactionType() == TransactionType.SELL).orElse(false))
            created += persist(user, symbol, AlertType.INSIDER_SELL, BigDecimal.ZERO);
        List<FundamentalSnapshot> snapshots = fundamentals.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.QUARTERLY);
        if (snapshots.size() >= 2 && snapshots.get(0).getEps() != null && snapshots.get(1).getEps() != null
                && snapshots.get(0).getEps().compareTo(snapshots.get(1).getEps()) < 0)
            created += persist(user, symbol, AlertType.EARNINGS_SURPRISE, snapshots.get(1).getEps());
        return created;
    }

    private int rebalanceRule(User user, String symbol) {
        boolean pendingAction = rebalanceProposals.findByPortfolio_UserAndStatus(user, "PENDING").stream()
                .flatMap(proposal -> proposal.getLines().stream()).anyMatch(line -> symbol.equalsIgnoreCase(line.getSymbol())
                        && line.getTargetQuantity().compareTo(line.getCurrentQuantity()) != 0);
        return pendingAction ? persist(user, symbol, AlertType.REBALANCE_NEEDED, BigDecimal.ZERO) : 0;
    }

    private int persist(User user, String symbol, AlertType type, BigDecimal threshold) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        if (alerts.existsByUserAndSymbolAndAlertTypeAndTriggeredAtBetween(user, symbol, type, start, start.plusDays(1))) return 0;
        Alert alert = new Alert(); alert.setUser(user); alert.setSymbol(symbol); alert.setAlertType(type);
        alert.setThreshold(threshold); alert.setStatus(AlertStatus.ACTIVE); alert.setTriggeredAt(LocalDateTime.now());
        alert.setPriority(priorityFor(type));
        alerts.save(alert);
        alertDeliveryService.deliver(alert);
        return 1;
    }

    private AlertPriority priorityFor(AlertType type) {
        return switch (type) {
            case DIVIDEND_CUT, INSIDER_SELL, EARNINGS_SURPRISE, REBALANCE_NEEDED -> AlertPriority.HIGH;
            default -> AlertPriority.NORMAL;
        };
    }
}
