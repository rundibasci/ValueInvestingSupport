package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "seed_run_outcome")
public class SeedRunOutcome {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "seed_run_id") private SeedRun seedRun;
    @Column(nullable = false) private int position;
    @Column(nullable = false, length = 20) private String symbol;
    @Column(nullable = false, length = 30) private String status;
    @Column(length = 60) private String source;
    @Column(length = 80) private String reasonCode;
    @Column(length = 500) private String reason;
    @Column(length = 1000) private String fallbackReason;
    @Column(length = 500) private String errorMessage;
    @Column(nullable = false) private LocalDateTime completedAt;

    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public SeedRun getSeedRun() { return seedRun; } public void setSeedRun(SeedRun value) { this.seedRun = value; }
    public int getPosition() { return position; } public void setPosition(int value) { this.position = value; }
    public String getSymbol() { return symbol; } public void setSymbol(String value) { this.symbol = value; }
    public String getStatus() { return status; } public void setStatus(String value) { this.status = value; }
    public String getSource() { return source; } public void setSource(String value) { this.source = value; }
    public String getReasonCode() { return reasonCode; } public void setReasonCode(String value) { this.reasonCode = value; }
    public String getReason() { return reason; } public void setReason(String value) { this.reason = value; }
    public String getFallbackReason() { return fallbackReason; } public void setFallbackReason(String value) { this.fallbackReason = value; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String value) { this.errorMessage = value; }
    public LocalDateTime getCompletedAt() { return completedAt; } public void setCompletedAt(LocalDateTime value) { this.completedAt = value; }
}
