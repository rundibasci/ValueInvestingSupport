package it.mazzoni.vis.jobs;

import it.mazzoni.vis.domain.entity.InsiderTrade;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.TransactionType;
import it.mazzoni.vis.domain.repository.HoldingRepository;
import it.mazzoni.vis.domain.repository.InsiderTradeRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.WatchlistItemRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.marketdata.fmp.dto.FmpInsiderTradingEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class InsiderTradingJob {

    private static final Logger log = LoggerFactory.getLogger(InsiderTradingJob.class);

    private final MarketDataClient marketDataClient;
    private final SecurityRepository securityRepository;
    private final InsiderTradeRepository insiderTradeRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final HoldingRepository holdingRepository;
    private final JobRunLogger jobRunLogger;

    public InsiderTradingJob(MarketDataClient marketDataClient,
                              SecurityRepository securityRepository,
                              InsiderTradeRepository insiderTradeRepository,
                              WatchlistItemRepository watchlistItemRepository,
                              HoldingRepository holdingRepository,
                              JobRunLogger jobRunLogger) {
        this.marketDataClient = marketDataClient;
        this.securityRepository = securityRepository;
        this.insiderTradeRepository = insiderTradeRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.holdingRepository = holdingRepository;
        this.jobRunLogger = jobRunLogger;
    }

    @Scheduled(cron = "${app.jobs.cron.insider-trading}")
    public void run() {
        jobRunLogger.run("insider-trading", this::execute);
    }

    @Transactional
    public int execute() {
        Set<String> symbols = new LinkedHashSet<>();
        symbols.addAll(watchlistItemRepository.findAllDistinctSymbols());
        symbols.addAll(holdingRepository.findAllDistinctSymbols());

        int count = 0;
        for (String symbol : symbols) {
            Optional<Security> secOpt = securityRepository.findBySymbol(symbol.toUpperCase());
            if (secOpt.isEmpty()) continue;
            Security security = secOpt.get();

            try {
                List<FmpInsiderTradingEntry> entries = marketDataClient.getInsiderTransactions(symbol);
                for (FmpInsiderTradingEntry entry : entries) {
                    if (entry.transactionDate() == null || entry.reportingName() == null) continue;
                    LocalDate tradeDate = LocalDate.parse(entry.transactionDate());
                    if (insiderTradeRepository.existsBySecurityAndTradeDateAndInsiderName(
                            security, tradeDate, entry.reportingName())) continue;

                    InsiderTrade trade = new InsiderTrade();
                    trade.setSecurity(security);
                    trade.setTradeDate(tradeDate);
                    trade.setInsiderName(entry.reportingName());
                    trade.setTitle(entry.title());
                    trade.setTransactionType(resolveType(entry.transactionType()));
                    trade.setShares(entry.securitiesTransacted());
                    trade.setPricePerShare(entry.price());
                    insiderTradeRepository.save(trade);
                    count++;
                }
            } catch (MarketDataException e) {
                log.debug("Insider trading skipped for {}: {}", symbol, e.getMessage());
            }
        }
        return count;
    }

    private TransactionType resolveType(String fmpType) {
        if (fmpType == null) return TransactionType.BUY;
        String upper = fmpType.toUpperCase();
        return upper.contains("SALE") || upper.contains("SELL") || upper.startsWith("S-")
                ? TransactionType.SELL
                : TransactionType.BUY;
    }
}
