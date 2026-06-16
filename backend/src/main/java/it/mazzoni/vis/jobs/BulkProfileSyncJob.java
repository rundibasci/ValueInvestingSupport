package it.mazzoni.vis.jobs;

import it.mazzoni.vis.config.JobsProperties;
import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.marketdata.fmp.dto.FmpStockListEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class BulkProfileSyncJob {

    private static final Logger log = LoggerFactory.getLogger(BulkProfileSyncJob.class);

    private final MarketDataClient marketDataClient;
    private final SecurityRepository securityRepository;
    private final JobRunLogger jobRunLogger;
    private final JobsProperties jobsProperties;

    public BulkProfileSyncJob(MarketDataClient marketDataClient,
                               SecurityRepository securityRepository,
                               JobRunLogger jobRunLogger,
                               JobsProperties jobsProperties) {
        this.marketDataClient = marketDataClient;
        this.securityRepository = securityRepository;
        this.jobRunLogger = jobRunLogger;
        this.jobsProperties = jobsProperties;
    }

    @Scheduled(cron = "${app.jobs.cron.bulk-profile}")
    public void run() {
        jobRunLogger.run("bulk-profile-sync", this::execute);
    }

    @Transactional
    public int execute() {
        int count = 0;
        for (String exchange : jobsProperties.exchanges()) {
            List<FmpStockListEntry> entries = marketDataClient.listSymbols(exchange);
            for (FmpStockListEntry entry : entries) {
                upsertSecurity(entry);
                count++;
            }
        }
        return count;
    }

    private void upsertSecurity(FmpStockListEntry entry) {
        Security security = securityRepository.findBySymbol(entry.symbol().toUpperCase())
                .orElseGet(() -> {
                    Security s = new Security();
                    s.setSymbol(entry.symbol().toUpperCase());
                    s.setCompanyName(entry.name() != null ? entry.name() : entry.symbol());
                    return s;
                });

        security.setExchange(entry.exchangeShortName());

        try {
            CompanyProfile profile = marketDataClient.getProfile(entry.symbol());
            security.setCompanyName(profile.companyName() != null ? profile.companyName() : security.getCompanyName());
            security.setSector(profile.sector());
            security.setCountry(profile.country());
            security.setCurrency(profile.currency());
            if (profile.marketCap() != null) security.setMarketCap(profile.marketCap());
        } catch (MarketDataException e) {
            log.debug("Profile fetch skipped for {}: {}", entry.symbol(), e.getMessage());
        }

        securityRepository.save(security);
    }
}
