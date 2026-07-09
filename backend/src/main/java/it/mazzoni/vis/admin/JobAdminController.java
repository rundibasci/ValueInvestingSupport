package it.mazzoni.vis.admin;

import it.mazzoni.vis.jobs.BulkDcfSyncJob;
import it.mazzoni.vis.jobs.BulkFundamentalsSyncJob;
import it.mazzoni.vis.jobs.BulkProfileSyncJob;
import it.mazzoni.vis.jobs.BulkRatiosSyncJob;
import it.mazzoni.vis.jobs.DividendUpdateJob;
import it.mazzoni.vis.jobs.InsiderTradingJob;
import it.mazzoni.vis.jobs.QuoteRefreshJob;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/jobs")
@Validated
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
        register("bulk-profile-sync", "bulk-profile", bulkProfileSyncJob::execute);
        register("bulk-fundamentals-sync", "bulk-fundamentals", bulkFundamentalsSyncJob::execute);
        register("bulk-ratios-sync", "bulk-ratios", bulkRatiosSyncJob::execute);
        register("bulk-dcf-sync", "bulk-dcf", bulkDcfSyncJob::execute);
        register("quote-refresh", "quote-refresh", quoteRefreshJob::execute);
        register("dividend-update", "dividend-update", dividendUpdateJob::execute);
        register("insider-trading", "insider-trading", insiderTradingJob::execute);
    }

    @GetMapping
    List<JobSummaryResponse> listJobs() {
        return jobAdminService.listJobs(jobRegistry);
    }

    @GetMapping("/monitor")
    List<JobMonitorResponse> monitorJobs() {
        return jobAdminService.monitorJobs(jobRegistry);
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
    ResponseEntity<JobTriggerResponse> runJob(@PathVariable String jobName,
                                              @RequestBody(required = false) JobRunRequest request) {
        JobDefinition job = jobRegistry.get(jobName);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.accepted().body(jobAdminService.trigger(job, request));
    }

    @PutMapping("/{jobName}/enabled")
    ResponseEntity<JobSummaryResponse> updateEnabled(@PathVariable String jobName,
                                                     @Valid @RequestBody JobEnabledRequest request) {
        if (!jobRegistry.containsKey(jobName)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(jobAdminService.updateEnabled(jobRegistry.get(jobName), request.enabled()));
    }

    @PutMapping("/{jobName}/cron")
    ResponseEntity<JobSummaryResponse> updateCron(@PathVariable String jobName,
                                                  @Valid @RequestBody JobCronRequest request) {
        if (!jobRegistry.containsKey(jobName)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(jobAdminService.updateCron(jobRegistry.get(jobName), request.cron()));
    }

    @GetMapping("/runs/{jobRunId}/status")
    ResponseEntity<JobRunStatusResponse> runStatus(@PathVariable UUID jobRunId) {
        return jobAdminService.runStatus(jobRunId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(JobRunConflictException.class)
    ResponseEntity<JobTriggerResponse> duplicateRun(JobRunConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new JobTriggerResponse(ex.jobName(), "already-running", ex.activeRunId()));
    }

    private void register(String jobName, String cronKey, java.util.function.Supplier<Integer> runner) {
        jobRegistry.put(jobName, new JobDefinition(jobName, cronKey, runner));
    }
}
