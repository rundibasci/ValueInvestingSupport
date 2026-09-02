package it.mazzoni.vis.jobs;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
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
public class BulkFundamentalsSyncJob implements CloudRunJob {

    private static final Logger log = LoggerFactory.getLogger(BulkFundamentalsSyncJob.class);

    private final MarketDataClient marketDataClient;
    private final SecurityRepository securityRepository;
    private final FundamentalSnapshotRepository snapshotRepository;
    private final JobRunLogger jobRunLogger;
    private final IngestionEventRecorder eventRecorder;

    public BulkFundamentalsSyncJob(MarketDataClient marketDataClient,
                                    SecurityRepository securityRepository,
                                    FundamentalSnapshotRepository snapshotRepository,
                                    JobRunLogger jobRunLogger,
                                    IngestionEventRecorder eventRecorder) {
        this.marketDataClient = marketDataClient;
        this.securityRepository = securityRepository;
        this.snapshotRepository = snapshotRepository;
        this.jobRunLogger = jobRunLogger;
        this.eventRecorder = eventRecorder;
    }

    @Override
    public String jobKey() {
        return "bulk-fundamentals-sync";
    }

    @Scheduled(cron = "${app.jobs.cron.bulk-fundamentals}")
    @Override
    public void run() {
        jobRunLogger.run(jobKey(), this::execute);
    }

    @Transactional
    public int execute() {
        List<Security> securities = securityRepository.findAll();
        LocalDate today = LocalDate.now();
        int count = 0;

        for (Security security : securities) {
            if (snapshotRepository.existsBySecurityAndPeriodAndReportDate(security, Period.ANNUAL, today)) {
                eventRecorder.skipped(security.getSymbol(), "fundamentals", "already ingested for report date");
                continue;
            }
            try {
                it.mazzoni.vis.domain.FundamentalSnapshot data = marketDataClient.getFundamentals(security.getSymbol());
                FundamentalSnapshot entity = toEntity(security, data, today);
                snapshotRepository.save(entity);
                eventRecorder.success(security.getSymbol(), "fundamentals");
                count++;
            } catch (MarketDataException e) {
                log.debug("Fundamentals skipped for {}: {}", security.getSymbol(), e.getMessage());
                eventRecorder.failed(security.getSymbol(), "fundamentals", e);
            }
        }
        return count;
    }

    private FundamentalSnapshot toEntity(Security security,
                                          it.mazzoni.vis.domain.FundamentalSnapshot data,
                                          LocalDate reportDate) {
        FundamentalSnapshot e = new FundamentalSnapshot();
        e.setSecurity(security);
        e.setPeriod(Period.ANNUAL);
        e.setReportDate(reportDate);
        e.setEps(data.epsTtm());
        e.setEpsDiluted(data.epsTtm());
        e.setSharesOutstanding(data.sharesOutstanding());
        e.setTotalDebt(data.totalDebt());
        e.setCash(data.cash());
        if (data.revenueHistory() != null && !data.revenueHistory().isEmpty())
            e.setRevenue(data.revenueHistory().get(0));
        if (data.netIncomeHistory() != null && !data.netIncomeHistory().isEmpty())
            e.setNetIncome(data.netIncomeHistory().get(0));
        if (data.fcfHistory() != null && !data.fcfHistory().isEmpty())
            e.setFreeCashFlow(data.fcfHistory().get(0));
        // RM1 (specs/sector-aware-valuation-metrics.md): FFO/Debt-EBITDA inputs; empty/null for
        // Yahoo-sourced snapshots (Design Principle 5 — no equivalent Yahoo field).
        if (data.depreciationAndAmortizationHistory() != null && !data.depreciationAndAmortizationHistory().isEmpty())
            e.setDepreciationAndAmortization(data.depreciationAndAmortizationHistory().get(0));
        if (data.ebitdaHistory() != null && !data.ebitdaHistory().isEmpty())
            e.setEbitda(data.ebitdaHistory().get(0));
        // RM2 (specs/sector-aware-valuation-metrics.md §4.2): AFFO's recurring-capex input.
        if (data.capitalExpenditureHistory() != null && !data.capitalExpenditureHistory().isEmpty())
            e.setCapitalExpenditure(data.capitalExpenditureHistory().get(0));
        // RM2: EBITDA interest coverage input, confirmed live against O/PLD/SPG during RM2.
        if (data.interestExpenseHistory() != null && !data.interestExpenseHistory().isEmpty())
            e.setInterestExpense(data.interestExpenseHistory().get(0));
        return e;
    }
}
