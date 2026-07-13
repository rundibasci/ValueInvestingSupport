package it.mazzoni.vis.jobs;

import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
public class BulkRatiosSyncJob {

    private static final Logger log = LoggerFactory.getLogger(BulkRatiosSyncJob.class);

    private final MarketDataClient marketDataClient;
    private final SecurityRepository securityRepository;
    private final RatioSnapshotRepository ratioRepository;
    private final JobRunLogger jobRunLogger;
    private final IngestionEventRecorder eventRecorder;

    public BulkRatiosSyncJob(MarketDataClient marketDataClient,
                               SecurityRepository securityRepository,
                               RatioSnapshotRepository ratioRepository,
                               JobRunLogger jobRunLogger,
                               IngestionEventRecorder eventRecorder) {
        this.marketDataClient = marketDataClient;
        this.securityRepository = securityRepository;
        this.ratioRepository = ratioRepository;
        this.jobRunLogger = jobRunLogger;
        this.eventRecorder = eventRecorder;
    }

    @Scheduled(cron = "${app.jobs.cron.bulk-ratios}")
    public void run() {
        jobRunLogger.run("bulk-ratios-sync", this::execute);
    }

    @Transactional
    public int execute() {
        List<Security> securities = securityRepository.findAll();
        LocalDate today = LocalDate.now();
        int count = 0;

        for (Security security : securities) {
            if (ratioRepository.existsBySecurityAndPeriodAndReportDate(security, Period.TTM, today)) {
                eventRecorder.skipped(security.getSymbol(), "ratios", "already ingested for report date");
                continue;
            }
            try {
                it.mazzoni.vis.domain.RatioSnapshot data = marketDataClient.getRatios(security.getSymbol());
                RatioSnapshot entity = toEntity(security, data, today);
                ratioRepository.save(entity);
                eventRecorder.success(security.getSymbol(), "ratios");
                count++;
            } catch (MarketDataException e) {
                log.debug("Ratios skipped for {}: {}", security.getSymbol(), e.getMessage());
                eventRecorder.failed(security.getSymbol(), "ratios", e);
            }
        }
        return count;
    }

    private RatioSnapshot toEntity(Security security,
                                    it.mazzoni.vis.domain.RatioSnapshot data,
                                    LocalDate reportDate) {
        RatioSnapshot e = new RatioSnapshot();
        e.setSecurity(security);
        e.setPeriod(Period.TTM);
        e.setReportDate(reportDate);
        e.setPeRatio(data.peRatio());
        e.setPbRatio(data.priceToBook());
        e.setRoe(data.roe());
        e.setRoa(data.roa());
        e.setRoic(data.roic());
        e.setCurrentRatio(data.currentRatio());
        e.setQuickRatio(data.quickRatio());
        e.setDebtToEquity(data.debtToEquity());
        e.setInterestCoverage(data.interestCoverage());
        e.setDividendYield(data.dividendYield());
        e.setPayoutRatio(data.payoutRatio());
        return e;
    }
}
