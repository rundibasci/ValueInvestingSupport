package it.mazzoni.vis.admin;

import it.mazzoni.vis.config.JobsProperties;
import it.mazzoni.vis.domain.entity.IngestionEvent;
import it.mazzoni.vis.domain.entity.JobRunLog;
import it.mazzoni.vis.domain.repository.IngestionEventRepository;
import it.mazzoni.vis.domain.repository.JobRunLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class JobAdminService {

    private final JobRunLogRepository jobRunLogRepository;
    private final IngestionEventRepository ingestionEventRepository;
    private final JobsProperties jobsProperties;

    public JobAdminService(JobRunLogRepository jobRunLogRepository,
                           IngestionEventRepository ingestionEventRepository,
                           JobsProperties jobsProperties) {
        this.jobRunLogRepository = jobRunLogRepository;
        this.ingestionEventRepository = ingestionEventRepository;
        this.jobsProperties = jobsProperties;
    }

    public List<JobSummaryResponse> listJobs(Map<String, JobDefinition> jobRegistry) {
        return jobRegistry.values().stream()
                .map(definition -> new JobSummaryResponse(
                        definition.jobName(),
                        jobsProperties.cronFor(definition.cronKey()),
                        jobsProperties.enabled(),
                        jobRunLogRepository.findTop1ByJobNameOrderByStartedAtDesc(definition.jobName())
                                .map(this::toRunSummary)
                                .orElse(null)
                ))
                .toList();
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
