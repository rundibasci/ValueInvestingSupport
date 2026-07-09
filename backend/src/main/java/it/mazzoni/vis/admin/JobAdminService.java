package it.mazzoni.vis.admin;

import it.mazzoni.vis.config.JobsProperties;
import it.mazzoni.vis.domain.entity.IngestionEvent;
import it.mazzoni.vis.domain.entity.JobRunLog;
import it.mazzoni.vis.domain.entity.JobRuntimeSetting;
import it.mazzoni.vis.domain.repository.IngestionEventRepository;
import it.mazzoni.vis.domain.repository.JobRunLogRepository;
import it.mazzoni.vis.domain.repository.JobRuntimeSettingRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class JobAdminService {

    private static final Logger log = LoggerFactory.getLogger(JobAdminService.class);

    private final JobRunLogRepository jobRunLogRepository;
    private final IngestionEventRepository ingestionEventRepository;
    private final JobRuntimeSettingRepository jobRuntimeSettingRepository;
    private final JobsProperties jobsProperties;

    public JobAdminService(JobRunLogRepository jobRunLogRepository,
                           IngestionEventRepository ingestionEventRepository,
                           JobRuntimeSettingRepository jobRuntimeSettingRepository,
                           JobsProperties jobsProperties) {
        this.jobRunLogRepository = jobRunLogRepository;
        this.ingestionEventRepository = ingestionEventRepository;
        this.jobRuntimeSettingRepository = jobRuntimeSettingRepository;
        this.jobsProperties = jobsProperties;
    }

    public List<JobSummaryResponse> listJobs(Map<String, JobDefinition> jobRegistry) {
        return jobRegistry.values().stream()
                .map(this::summary)
                .toList();
    }

    public List<JobMonitorResponse> monitorJobs(Map<String, JobDefinition> jobRegistry) {
        return jobRegistry.values().stream()
                .map(this::monitor)
                .toList();
    }

    @Transactional
    public JobSummaryResponse updateEnabled(JobDefinition definition, boolean enabled) {
        JobRuntimeSetting setting = settingFor(definition.jobName());
        setting.setEnabled(enabled);
        jobRuntimeSettingRepository.save(setting);
        log.info("job_runtime_enabled_updated jobName={} enabled={}", definition.jobName(), enabled);
        return summary(definition);
    }

    @Transactional
    public JobSummaryResponse updateCron(JobDefinition definition, String cron) {
        CronExpression.parse(cron);
        JobRuntimeSetting setting = settingFor(definition.jobName());
        setting.setCronExpression(cron);
        jobRuntimeSettingRepository.save(setting);
        log.info("job_runtime_cron_updated jobName={} cron={}", definition.jobName(), cron);
        return summary(definition);
    }

    public JobTriggerResponse trigger(JobDefinition definition, JobRunRequest request) {
        JobRuntimeSetting setting = settingFor(definition.jobName());
        Optional<JobRunLog> active = runningRun(definition.jobName());
        if (active.isPresent()) {
            log.warn("job_manual_run_duplicate_blocked jobName={} activeRunId={}", definition.jobName(), active.get().getId());
            throw new JobRunConflictException(definition.jobName(), active.get().getId());
        }

        JobRunLog runLog = new JobRunLog();
        runLog.setJobName(definition.jobName());
        runLog.setStartedAt(LocalDateTime.now());
        runLog.setScopeSymbols(normalizeCsv(request != null ? request.symbols() : null));
        runLog.setScopeExchange(normalizeToken(request != null ? request.exchange() : null));
        runLog.setScopeDataTypes(normalizeCsv(request != null ? request.dataTypes() : null));

        if (!setting.isEnabled()) {
            runLog.setCompletedAt(LocalDateTime.now());
            runLog.setStatus("SKIPPED");
            runLog.setRecordsProcessed(0);
            runLog.setErrorMessage("Job is disabled by runtime setting");
            JobRunLog saved = jobRunLogRepository.save(runLog);
            log.info("job_manual_run_skipped jobName={} jobRunId={} reason=disabled", definition.jobName(), saved.getId());
            return new JobTriggerResponse(definition.jobName(), "skipped", saved.getId());
        }

        runLog.setStatus("RUNNING");
        JobRunLog saved = jobRunLogRepository.save(runLog);
        log.info("job_manual_run_started jobName={} jobRunId={} symbols={} exchange={} dataTypes={}",
                definition.jobName(), saved.getId(), saved.getScopeSymbols(), saved.getScopeExchange(), saved.getScopeDataTypes());
        CompletableFuture.runAsync(() -> runAsync(saved.getId(), definition));
        return new JobTriggerResponse(definition.jobName(), "triggered", saved.getId());
    }

    public Optional<JobRunStatusResponse> runStatus(UUID runId) {
        return jobRunLogRepository.findById(runId).map(log -> {
            long errors = ingestionEventRepository.count((root, query, builder) -> builder.and(
                    builder.equal(root.get("jobRunId"), runId),
                    builder.equal(root.get("status"), "FAILED")
            ));
            return new JobRunStatusResponse(
                    log.getId(),
                    log.getJobName(),
                    log.getStatus(),
                    log.getRecordsProcessed(),
                    countSymbols(log.getScopeSymbols()),
                    elapsedSeconds(log),
                    errors,
                    log.getScopeSymbols(),
                    log.getScopeExchange(),
                    log.getScopeDataTypes(),
                    log.getStartedAt(),
                    log.getCompletedAt(),
                    log.getErrorMessage()
            );
        });
    }

    public PageResponse<JobRunSummaryResponse> history(String jobName, int page, int size) {
        Page<JobRunSummaryResponse> result = jobRunLogRepository
                .findByJobNameOrderByStartedAtDesc(jobName, pageRequest(page, size, "startedAt"))
                .map(this::toRunSummary);
        return PageResponse.from(result);
    }

    public PageResponse<IngestionEventResponse> events(String jobName,
                                                       UUID runId,
                                                       String symbol,
                                                       String status,
                                                       int page,
                                                       int size) {
        Specification<IngestionEvent> spec = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("jobName"), jobName));
            if (runId != null) {
                predicates.add(builder.equal(root.get("jobRunId"), runId));
            }
            if (symbol != null && !symbol.isBlank()) {
                predicates.add(builder.equal(root.get("symbol"), symbol.toUpperCase(Locale.ROOT)));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(builder.equal(root.get("status"), status.toUpperCase(Locale.ROOT)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        Page<IngestionEventResponse> result = ingestionEventRepository
                .findAll(spec, pageRequest(page, size, "occurredAt"))
                .map(this::toEventResponse);
        return PageResponse.from(result);
    }

    private Pageable pageRequest(int page, int size, String sortField) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 200));
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, sortField));
    }

    private JobSummaryResponse summary(JobDefinition definition) {
        JobRuntimeSetting setting = settingFor(definition.jobName());
        String effectiveCron = setting.getCronExpression() != null && !setting.getCronExpression().isBlank()
                ? setting.getCronExpression()
                : jobsProperties.cronFor(definition.cronKey());
        return new JobSummaryResponse(
                definition.jobName(),
                effectiveCron,
                jobsProperties.enabled() && setting.isEnabled(),
                jobRunLogRepository.findTop1ByJobNameOrderByStartedAtDesc(definition.jobName())
                        .map(this::toRunSummary)
                        .orElse(null)
        );
    }

    private JobMonitorResponse monitor(JobDefinition definition) {
        JobRuntimeSetting setting = settingFor(definition.jobName());
        String effectiveCron = setting.getCronExpression() != null && !setting.getCronExpression().isBlank()
                ? setting.getCronExpression()
                : jobsProperties.cronFor(definition.cronKey());
        SchedulePreview schedulePreview = schedulePreview(effectiveCron);
        JobRunSummaryResponse running = runningRun(definition.jobName()).map(this::toRunSummary).orElse(null);
        JobRunSummaryResponse lastRun = jobRunLogRepository.findTop1ByJobNameOrderByStartedAtDesc(definition.jobName())
                .map(this::toRunSummary)
                .orElse(null);
        JobRunSummaryResponse lastSuccess = jobRunLogRepository
                .findTop1ByJobNameAndStatusOrderByStartedAtDesc(definition.jobName(), "SUCCESS")
                .map(this::toRunSummary)
                .orElse(null);
        JobRunSummaryResponse lastFailure = jobRunLogRepository
                .findTop1ByJobNameAndStatusOrderByStartedAtDesc(definition.jobName(), "FAILED")
                .map(this::toRunSummary)
                .orElse(null);
        JobRunLog durationSource = runningRun(definition.jobName())
                .orElseGet(() -> jobRunLogRepository.findTop1ByJobNameOrderByStartedAtDesc(definition.jobName()).orElse(null));
        return new JobMonitorResponse(
                definition.jobName(),
                effectiveCron,
                jobsProperties.enabled() && setting.isEnabled(),
                schedulePreview.nextRunAt(),
                schedulePreview.error(),
                running != null ? "RUNNING" : lastRun != null ? lastRun.status() : "IDLE",
                durationSource != null ? elapsedSeconds(durationSource) : 0L,
                lastRun != null ? dataSource(lastRun.id()) : null,
                running,
                lastRun,
                lastSuccess,
                lastFailure,
                latestError(lastRun, lastFailure)
        );
    }

    private Optional<JobRunLog> runningRun(String jobName) {
        return jobRunLogRepository.findTop1ByJobNameAndStatusOrderByStartedAtDesc(jobName, "RUNNING");
    }

    private SchedulePreview schedulePreview(String cron) {
        if (cron == null || cron.isBlank() || "-".equals(cron)) {
            return new SchedulePreview(null, "No cron expression configured");
        }
        try {
            return new SchedulePreview(CronExpression.parse(cron).next(LocalDateTime.now()), null);
        } catch (IllegalArgumentException e) {
            return new SchedulePreview(null, e.getMessage());
        }
    }

    private String dataSource(UUID runId) {
        return ingestionEventRepository.findTop1ByJobRunIdOrderByOccurredAtDesc(runId)
                .map(IngestionEvent::getSource)
                .orElse(null);
    }

    private String latestError(JobRunSummaryResponse lastRun, JobRunSummaryResponse lastFailure) {
        if (lastRun != null && lastRun.errorMessage() != null && !lastRun.errorMessage().isBlank()) {
            return lastRun.errorMessage();
        }
        if (lastFailure != null) {
            return lastFailure.errorMessage();
        }
        return null;
    }

    private JobRuntimeSetting settingFor(String jobName) {
        return jobRuntimeSettingRepository.findById(jobName)
                .orElseGet(() -> {
                    JobRuntimeSetting setting = new JobRuntimeSetting();
                    setting.setJobName(jobName);
                    setting.setEnabled(true);
                    return setting;
                });
    }

    private void runAsync(UUID runId, JobDefinition definition) {
        MDC.put("job.name", definition.jobName());
        MDC.put("job.run.id", runId.toString());
        try {
            int count = definition.runner().get();
            jobRunLogRepository.findById(runId).ifPresent(log -> {
                log.setCompletedAt(LocalDateTime.now());
                log.setStatus("SUCCESS");
                log.setRecordsProcessed(count);
                jobRunLogRepository.save(log);
            });
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            jobRunLogRepository.findById(runId).ifPresent(log -> {
                log.setCompletedAt(LocalDateTime.now());
                log.setStatus("FAILED");
                log.setErrorMessage(message);
                jobRunLogRepository.save(log);
            });
        } finally {
            MDC.remove("job.name");
            MDC.remove("job.run.id");
        }
    }

    private record SchedulePreview(LocalDateTime nextRunAt, String error) {
    }

    private String normalizeCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return String.join(",", List.of(raw.split(",")).stream()
                .map(this::normalizeToken)
                .filter(token -> token != null && !token.isBlank())
                .toList());
    }

    private String normalizeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private Integer countSymbols(String symbols) {
        if (symbols == null || symbols.isBlank()) {
            return null;
        }
        return (int) List.of(symbols.split(",")).stream()
                .filter(symbol -> !symbol.isBlank())
                .count();
    }

    private long elapsedSeconds(JobRunLog log) {
        LocalDateTime end = log.getCompletedAt() != null ? log.getCompletedAt() : LocalDateTime.now();
        return Math.max(0L, Duration.between(log.getStartedAt(), end).toSeconds());
    }

    private JobRunSummaryResponse toRunSummary(JobRunLog log) {
        return new JobRunSummaryResponse(
                log.getId(),
                log.getJobName(),
                log.getStartedAt(),
                log.getCompletedAt(),
                log.getStatus(),
                log.getRecordsProcessed(),
                log.getErrorMessage()
        );
    }

    private IngestionEventResponse toEventResponse(IngestionEvent event) {
        return new IngestionEventResponse(
                event.getId(),
                event.getJobRunId(),
                event.getJobName(),
                event.getSymbol(),
                event.getDataType(),
                event.getStatus(),
                event.getSource(),
                event.getErrorDetail(),
                event.getOccurredAt()
        );
    }
}
