package it.mazzoni.vis.jobs;

import it.mazzoni.vis.domain.entity.DividendRecord;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
import it.mazzoni.vis.domain.repository.HoldingRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.WatchlistItemRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.marketdata.fmp.dto.FmpDividendEntry;
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
public class DividendUpdateJob implements CloudRunJob {

    private static final Logger log = LoggerFactory.getLogger(DividendUpdateJob.class);

    private final MarketDataClient marketDataClient;
    private final SecurityRepository securityRepository;
    private final DividendRecordRepository dividendRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final HoldingRepository holdingRepository;
    private final JobRunLogger jobRunLogger;
    private final IngestionEventRecorder eventRecorder;

    public DividendUpdateJob(MarketDataClient marketDataClient,
                              SecurityRepository securityRepository,
                              DividendRecordRepository dividendRepository,
                              WatchlistItemRepository watchlistItemRepository,
                              HoldingRepository holdingRepository,
                              JobRunLogger jobRunLogger,
                              IngestionEventRecorder eventRecorder) {
        this.marketDataClient = marketDataClient;
        this.securityRepository = securityRepository;
        this.dividendRepository = dividendRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.holdingRepository = holdingRepository;
        this.jobRunLogger = jobRunLogger;
        this.eventRecorder = eventRecorder;
    }

    @Override
    public String jobKey() {
        return "dividend-update";
    }

    @Scheduled(cron = "${app.jobs.cron.dividend-update}")
    @Override
    public void run() {
        jobRunLogger.run(jobKey(), this::execute);
    }

    @Transactional
    public int execute() {
        Set<String> symbols = new LinkedHashSet<>();
        symbols.addAll(watchlistItemRepository.findAllDistinctSymbols());
        symbols.addAll(holdingRepository.findAllDistinctSymbols());

        int count = 0;
        for (String symbol : symbols) {
            Optional<Security> secOpt = securityRepository.findBySymbol(symbol.toUpperCase());
            if (secOpt.isEmpty()) {
                eventRecorder.skipped(symbol, "dividend", "security not found");
                continue;
            }
            Security security = secOpt.get();

            try {
                List<FmpDividendEntry> entries = marketDataClient.getDividendHistory(symbol);
                int inserted = 0;
                for (FmpDividendEntry entry : entries) {
                    if (entry.date() == null || entry.dividend() == null) continue;
                    LocalDate exDate = LocalDate.parse(entry.date());
                    if (dividendRepository.findBySecurityAndExDividendDate(security, exDate).isPresent()) continue;

                    DividendRecord record = new DividendRecord();
                    record.setSecurity(security);
                    record.setExDividendDate(exDate);
                    record.setAmount(entry.dividend());
                    if (entry.paymentDate() != null && !entry.paymentDate().isBlank()) {
                        try { record.setPaymentDate(LocalDate.parse(entry.paymentDate())); }
                        catch (Exception ignored) {}
                    }
                    dividendRepository.save(record);
                    count++;
                    inserted++;
                }
                if (inserted > 0) {
                    eventRecorder.success(symbol, "dividend");
                } else {
                    eventRecorder.skipped(symbol, "dividend", "no new dividend records");
                }
            } catch (MarketDataException e) {
                log.debug("Dividend update skipped for {}: {}", symbol, e.getMessage());
                eventRecorder.failed(symbol, "dividend", e);
            }
        }
        return count;
    }
}
