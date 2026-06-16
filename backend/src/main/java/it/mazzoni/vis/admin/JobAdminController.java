package it.mazzoni.vis.admin;

import it.mazzoni.vis.jobs.BulkDcfSyncJob;
import it.mazzoni.vis.jobs.BulkFundamentalsSyncJob;
import it.mazzoni.vis.jobs.BulkProfileSyncJob;
import it.mazzoni.vis.jobs.BulkRatiosSyncJob;
import it.mazzoni.vis.jobs.DividendUpdateJob;
import it.mazzoni.vis.jobs.InsiderTradingJob;
import it.mazzoni.vis.jobs.QuoteRefreshJob;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/admin/jobs")
public class JobAdminController {

    private final Map<String, Runnable> jobRegistry;

    public JobAdminController(BulkProfileSyncJob bulkProfileSyncJob,
                               BulkFundamentalsSyncJob bulkFundamentalsSyncJob,
                               BulkRatiosSyncJob bulkRatiosSyncJob,
                               BulkDcfSyncJob bulkDcfSyncJob,
                               QuoteRefreshJob quoteRefreshJob,
                               DividendUpdateJob dividendUpdateJob,
                               InsiderTradingJob insiderTradingJob) {
        this.jobRegistry = Map.of(
                "bulk-profile-sync",      bulkProfileSyncJob::run,
                "bulk-fundamentals-sync", bulkFundamentalsSyncJob::run,
                "bulk-ratios-sync",       bulkRatiosSyncJob::run,
                "bulk-dcf-sync",          bulkDcfSyncJob::run,
                "quote-refresh",          quoteRefreshJob::run,
                "dividend-update",        dividendUpdateJob::run,
                "insider-trading",        insiderTradingJob::run
        );
    }

    @PostMapping("/{jobName}/run")
    ResponseEntity<Map<String, String>> runJob(@PathVariable String jobName) {
        Runnable job = jobRegistry.get(jobName);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        CompletableFuture.runAsync(job);
        return ResponseEntity.accepted()
                .body(Map.of("jobName", jobName, "status", "triggered"));
    }
}
