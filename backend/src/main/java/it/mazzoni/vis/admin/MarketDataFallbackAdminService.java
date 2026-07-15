package it.mazzoni.vis.admin;

import it.mazzoni.vis.domain.entity.MarketDataFallbackEvent;
import it.mazzoni.vis.domain.repository.MarketDataFallbackEventRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MarketDataFallbackAdminService {

    private final MarketDataFallbackEventRepository repository;

    public MarketDataFallbackAdminService(MarketDataFallbackEventRepository repository) {
        this.repository = repository;
    }

    public PageResponse<MarketDataFallbackEventResponse> events(String symbol,
                                                                String operation,
                                                                String eventType,
                                                                String outcome,
                                                                String triggerReason,
                                                                UUID jobRunId,
                                                                LocalDateTime from,
                                                                LocalDateTime to,
                                                                int page,
                                                                int size) {
        Page<MarketDataFallbackEventResponse> result = repository.findAll(
                        specification(symbol, operation, eventType, outcome, triggerReason, jobRunId, from, to),
                        PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200)),
                                Sort.by(Sort.Direction.DESC, "occurredAt")))
                .map(this::response);
        return PageResponse.from(result);
    }

    public MarketDataFallbackSummaryResponse summary(String symbol,
                                                      String operation,
                                                      String eventType,
                                                      String outcome,
                                                      String triggerReason,
                                                      UUID jobRunId,
                                                      LocalDateTime from,
                                                      LocalDateTime to) {
        List<MarketDataFallbackEvent> events = repository.findAll(
                specification(symbol, operation, eventType, outcome, triggerReason, jobRunId, from, to),
                Sort.by(Sort.Direction.DESC, "occurredAt"));

        return new MarketDataFallbackSummaryResponse(
                events.size(),
                count(events, "PRIMARY_PROVIDER_FALLBACK", "SUCCESS"),
                count(events, "PRIMARY_PROVIDER_ENRICHMENT", "SUCCESS"),
                events.stream().filter(event -> "FAILED".equals(event.getOutcome())).count(),
                events.stream().filter(event -> "REJECTED".equals(event.getOutcome())).count(),
                events.stream().map(MarketDataFallbackEvent::getSymbol).distinct().count(),
                events.isEmpty() ? null : events.get(0).getOccurredAt(),
                grouped(events, MarketDataFallbackEvent::getTriggerReason),
                grouped(events, MarketDataFallbackEvent::getOperation),
                grouped(events, MarketDataFallbackEvent::getOutcome)
        );
    }

    private Specification<MarketDataFallbackEvent> specification(String symbol,
                                                                  String operation,
                                                                  String eventType,
                                                                  String outcome,
                                                                  String triggerReason,
                                                                  UUID jobRunId,
                                                                  LocalDateTime from,
                                                                  LocalDateTime to) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            addEqual(predicates, builder, root.get("symbol"), symbol);
            addEqual(predicates, builder, root.get("operation"), operation);
            addEqual(predicates, builder, root.get("eventType"), eventType);
            addEqual(predicates, builder, root.get("outcome"), outcome);
            addEqual(predicates, builder, root.get("triggerReason"), triggerReason);
            if (jobRunId != null) predicates.add(builder.equal(root.get("jobRunId"), jobRunId));
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null) predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), to));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addEqual(List<Predicate> predicates,
                          jakarta.persistence.criteria.CriteriaBuilder builder,
                          jakarta.persistence.criteria.Path<String> path,
                          String value) {
        if (value != null && !value.isBlank()) {
            predicates.add(builder.equal(path, value.trim().toUpperCase(Locale.ROOT)));
        }
    }

    private long count(List<MarketDataFallbackEvent> events, String eventType, String outcome) {
        return events.stream()
                .filter(event -> eventType.equals(event.getEventType()) && outcome.equals(event.getOutcome()))
                .count();
    }

    private Map<String, Long> grouped(List<MarketDataFallbackEvent> events,
                                      Function<MarketDataFallbackEvent, String> classifier) {
        return events.stream()
                .collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.counting()));
    }

    private MarketDataFallbackEventResponse response(MarketDataFallbackEvent event) {
        return new MarketDataFallbackEventResponse(
                event.getId(), event.getJobRunId(), event.getJobName(), event.getSymbol(),
                event.getOperation(), event.getEventType(), event.getTriggerReason(),
                event.getPrimaryProvider(), event.getFallbackProvider(), event.getPrimaryStatus(),
                event.getOutcome(), event.getMissingFields(), event.getAcceptedFields(),
                event.getErrorDetail(), event.getDurationMs(), event.getOccurredAt());
    }
}
