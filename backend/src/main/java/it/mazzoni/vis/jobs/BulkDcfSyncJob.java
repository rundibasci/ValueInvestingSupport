package it.mazzoni.vis.jobs;

import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class BulkDcfSyncJob {

    private static final String SOURCE = "FMP_DCF";
    private static final Logger log = LoggerFactory.getLogger(BulkDcfSyncJob.class);

    private final MarketDataClient marketDataClient;
    private final SecurityRepository securityRepository;
    private final ValuationResultRepository valuationRepository;
    private final JobRunLogger jobRunLogger;

    public BulkDcfSyncJob(MarketDataClient marketDataClient,
                           SecurityRepository securityRepository,
                           ValuationResultRepository valuationRepository,
                           JobRunLogger jobRunLogger) {
        this.marketDataClient = marketDataClient;
        this.securityRepository = securityRepository;
        this.valuationRepository = valuationRepository;
        this.jobRunLogger = jobRunLogger;
    }

    @Scheduled(cron = "${app.jobs.cron.bulk-dcf}")
    public void run() {
        jobRunLogger.run("bulk-dcf-sync", this::execute);
    }

    @Transactional
    public int execute() {
        List<Security> securities = securityRepository.findAll();
        LocalDate today = LocalDate.now();
        int count = 0;

        for (Security security : securities) {
            if (valuationRepository.existsBySecurityAndValuationDateAndSource(security, today, SOURCE)) {
                continue;
            }
            try {
                Optional<BigDecimal> dcf = marketDataClient.getFmpDcf(security.getSymbol());
                if (dcf.isEmpty()) continue;

                ValuationResult result = new ValuationResult();
                result.setSecurity(security);
                result.setValuationDate(today);
                result.setDcfFairValue(dcf.get());
                result.setSource(SOURCE);
                valuationRepository.save(result);
                count++;
            } catch (MarketDataException e) {
                log.debug("DCF skipped for {}: {}", security.getSymbol(), e.getMessage());
            }
        }
        return count;
    }
}
