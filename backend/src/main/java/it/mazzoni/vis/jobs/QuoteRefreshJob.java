package it.mazzoni.vis.jobs;

import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.HoldingRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.WatchlistItemRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Component
public class QuoteRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(QuoteRefreshJob.class);

    private final MarketDataClient marketDataClient;
    private final SecurityRepository securityRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final HoldingRepository holdingRepository;
    private final JobRunLogger jobRunLogger;
    private final IngestionEventRecorder eventRecorder;

    public QuoteRefreshJob(MarketDataClient marketDataClient,
                            SecurityRepository securityRepository,
                            PriceQuoteRepository priceQuoteRepository,
                            WatchlistItemRepository watchlistItemRepository,
                            HoldingRepository holdingRepository,
                            JobRunLogger jobRunLogger,
                            IngestionEventRecorder eventRecorder) {
        this.marketDataClient = marketDataClient;
        this.securityRepository = securityRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.holdingRepository = holdingRepository;
        this.jobRunLogger = jobRunLogger;
        this.eventRecorder = eventRecorder;
    }

    @Scheduled(cron = "${app.jobs.cron.quote-refresh}")
    public void run() {
        jobRunLogger.run("quote-refresh", this::execute);
    }

    @Transactional
    public int execute() {
        Set<String> symbols = new LinkedHashSet<>();
        symbols.addAll(watchlistItemRepository.findAllDistinctSymbols());
        symbols.addAll(holdingRepository.findAllDistinctSymbols());

        LocalDate today = LocalDate.now();
        int count = 0;

        for (String symbol : symbols) {
            Optional<Security> secOpt = securityRepository.findBySymbol(symbol.toUpperCase());
            if (secOpt.isEmpty()) {
                eventRecorder.skipped(symbol, "quote", "security not found");
                continue;
            }
            Security security = secOpt.get();

            if (priceQuoteRepository.existsBySecurityAndQuoteDate(security, today)) {
                eventRecorder.skipped(symbol, "quote", "already ingested for quote date");
                continue;
            }

            try {
                MarketPriceQuote quote = marketDataClient.getQuote(symbol);
                if (quote.price() == null) {
                    eventRecorder.skipped(symbol, "quote", "provider returned no price");
                    continue;
                }

                PriceQuote entity = new PriceQuote();
                entity.setSecurity(security);
                entity.setQuoteDate(today);
                entity.setClose(quote.price());
                priceQuoteRepository.save(entity);
                eventRecorder.success(symbol, "quote");
                count++;
            } catch (MarketDataException e) {
                log.debug("Quote skipped for {}: {}", symbol, e.getMessage());
                eventRecorder.failed(symbol, "quote", e);
            }
        }
        return count;
    }
}
