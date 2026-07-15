package it.mazzoni.vis.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "market_data_fallback_event")
public class MarketDataFallbackEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID jobRunId;

    @Column(length = 100)
    private String jobName;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 40)
    private String operation;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, length = 50)
    private String triggerReason;

    @Column(nullable = false, length = 30)
    private String primaryProvider;

    @Column(nullable = false, length = 30)
    private String fallbackProvider;

    @Column(length = 80)
    private String primaryStatus;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(length = 500)
    private String missingFields;

    @Column(length = 500)
    private String acceptedFields;

    @Column(length = 1000)
    private String errorDetail;

    @Column(nullable = false)
    private long durationMs;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getJobRunId() { return jobRunId; }
    public void setJobRunId(UUID jobRunId) { this.jobRunId = jobRunId; }
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getTriggerReason() { return triggerReason; }
    public void setTriggerReason(String triggerReason) { this.triggerReason = triggerReason; }
    public String getPrimaryProvider() { return primaryProvider; }
    public void setPrimaryProvider(String primaryProvider) { this.primaryProvider = primaryProvider; }
    public String getFallbackProvider() { return fallbackProvider; }
    public void setFallbackProvider(String fallbackProvider) { this.fallbackProvider = fallbackProvider; }
    public String getPrimaryStatus() { return primaryStatus; }
    public void setPrimaryStatus(String primaryStatus) { this.primaryStatus = primaryStatus; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getMissingFields() { return missingFields; }
    public void setMissingFields(String missingFields) { this.missingFields = missingFields; }
    public String getAcceptedFields() { return acceptedFields; }
    public void setAcceptedFields(String acceptedFields) { this.acceptedFields = acceptedFields; }
    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}

