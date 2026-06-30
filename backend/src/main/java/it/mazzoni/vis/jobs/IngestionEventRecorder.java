package it.mazzoni.vis.jobs;

import it.mazzoni.vis.domain.entity.IngestionEvent;
import it.mazzoni.vis.domain.repository.IngestionEventRepository;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class IngestionEventRecorder {

    private final IngestionEventRepository repository;
    private final String configuredSource;

    public IngestionEventRecorder(IngestionEventRepository repository,
                                  @Value("${market-data.source:unknown}") String configuredSource) {
        this.repository = repository;
        this.configuredSource = configuredSource;
    }

    public void success(String symbol, String dataType) {
        record(symbol, dataType, "SUCCESS", null);
    }

    public void skipped(String symbol, String dataType, String reason) {
        record(symbol, dataType, "SKIPPED", reason);
    }

    public void failed(String symbol, String dataType, Exception error) {
        String detail = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        record(symbol, dataType, "FAILED", detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String symbol, String dataType, String status, String errorDetail) {
        IngestionEvent event = new IngestionEvent();
        event.setJobRunId(currentRunId());
        event.setJobName(currentJobName());
        event.setSymbol(normalize(symbol));
        event.setDataType(dataType);
        event.setStatus(status);
        event.setSource(configuredSource);
        event.setErrorDetail(errorDetail);
        event.setOccurredAt(LocalDateTime.now());
        repository.save(event);
    }

    private UUID currentRunId() {
        String value = MDC.get("job.run.id");
        return value != null ? UUID.fromString(value) : null;
    }

    private String currentJobName() {
        String value = MDC.get("job.name");
        return value != null ? value : "unknown";
    }

    private String normalize(String symbol) {
        return symbol != null ? symbol.toUpperCase(Locale.ROOT) : null;
    }
}
