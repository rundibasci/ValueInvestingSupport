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
@Table(name = "ingestion_event")
public class IngestionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID jobRunId;

    @Column(nullable = false, length = 100)
    private String jobName;

    @Column(length = 20)
    private String symbol;

    @Column(nullable = false, length = 40)
    private String dataType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 30)
    private String source;

    @Column(columnDefinition = "TEXT")
    private String errorDetail;

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

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
