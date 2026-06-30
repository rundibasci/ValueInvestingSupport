package it.mazzoni.vis.admin;

import it.mazzoni.vis.jobs.BulkDcfSyncJob;
import it.mazzoni.vis.jobs.BulkFundamentalsSyncJob;
import it.mazzoni.vis.jobs.BulkProfileSyncJob;
import it.mazzoni.vis.jobs.BulkRatiosSyncJob;
import it.mazzoni.vis.jobs.DividendUpdateJob;
import it.mazzoni.vis.jobs.InsiderTradingJob;
import it.mazzoni.vis.jobs.QuoteRefreshJob;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/admin/jobs")
public class JobAdminController {

    private final Map<String, JobDefinition> jobRegistry;
    private final JobAdminService jobAdminService;

    public JobAdminController(BulkProfileSyncJob bulkProfileSyncJob,
                               BulkFundamentalsSyncJob bulkFundamentalsSyncJob,
                               BulkRatiosSyncJob bulkRatiosSyncJob,
                               BulkDcfSyncJob bulkDcfSyncJob,
                               QuoteRefreshJob quoteRefreshJob,
                               DividendUpdateJob dividendUpdateJob,
                               InsiderTradingJob insiderTradingJob,
                               JobAdminService jobAdminService) {
        this.jobAdminService = jobAdminService;
        this.jobRegistry = new LinkedHashMap<>();
        register("bulk-profile-sync", "bulk-profile", bulkProfileSyncJob::run);
        register("bulk-fundamentals-sync", "bulk-fundamentals", bulkFundamentalsSyncJob::run);
        register("bulk-ratios-sync", "bulk-ratios", bulkRatiosSyncJob::run);
        register("bulk-dcf-sync", "bulk-dcf", bulkDcfSyncJob::run);
        register("quote-refresh", "quote-refresh", quoteRefreshJob::run);
        register("dividend-update", "dividend-update", dividendUpdateJob::run);
        register("insider-trading", "insider-trading", insiderTradingJob::run);
    }

    @GetMapping
    List<JobSummaryResponse> listJobs() {
        return jobAdminService.listJobs(jobRegistry);
    }

    @GetMapping("/{jobName}/history")
    ResponseEntity<PageResponse<JobRunSummaryResponse>> history(@PathVariable String jobName,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        if (!jobRegistry.containsKey(jobName)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(jobAdminService.history(jobName, page, size));
    }

    @GetMapping("/{jobName}/events")
    ResponseEntity<PageResponse<IngestionEventResponse>> events(@PathVariable String jobName,
                                                                @RequestParam(required = false) UUID runId,
                                                                @RequestParam(required = false) String symbol,
                                                                @RequestParam(required = false) String status,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        if (!jobRegistry.containsKey(jobName)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(jobAdminService.events(jobName, runId, symbol, status, page, size));
    }

    @PostMapping("/{jobName}/run")
    ResponseEntity<Map<String, String>> runJob(@PathVariable String jobName) {
        JobDefinition job = jobRegistry.get(jobName);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        CompletableFuture.runAsync(job.runner());
        return ResponseEntity.accepted()
                .body(Map.of("jobName", jobName, "status", "triggered"));
    }

    private void register(String jobName, String cronKey, Runnable runner) {
        jobRegistry.put(jobName, new JobDefinition(jobName, cronKey, runner));
    }
}
