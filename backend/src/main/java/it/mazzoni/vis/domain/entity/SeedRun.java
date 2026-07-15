package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "seed_run")
public class SeedRun {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false, length = 40) private String scope;
    @Column(nullable = false, length = 64) private String requestFingerprint;
    @Column(nullable = false, columnDefinition = "TEXT") private String symbols;
    @Column(nullable = false, length = 30) private String status;
    @Column(nullable = false) private int totalCount;
    @Column(nullable = false) private int processedCount;
    @Column(nullable = false) private int succeededCount;
    @Column(nullable = false) private int partialCount;
    @Column(nullable = false) private int failedCount;
    @Column(length = 20) private String currentSymbol;
    @Column(length = 500) private String terminalReason;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; } public void setUser(User user) { this.user = user; }
    public String getScope() { return scope; } public void setScope(String scope) { this.scope = scope; }
    public String getRequestFingerprint() { return requestFingerprint; } public void setRequestFingerprint(String value) { this.requestFingerprint = value; }
    public String getSymbols() { return symbols; } public void setSymbols(String symbols) { this.symbols = symbols; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public int getTotalCount() { return totalCount; } public void setTotalCount(int value) { this.totalCount = value; }
    public int getProcessedCount() { return processedCount; } public void setProcessedCount(int value) { this.processedCount = value; }
    public int getSucceededCount() { return succeededCount; } public void setSucceededCount(int value) { this.succeededCount = value; }
    public int getPartialCount() { return partialCount; } public void setPartialCount(int value) { this.partialCount = value; }
    public int getFailedCount() { return failedCount; } public void setFailedCount(int value) { this.failedCount = value; }
    public String getCurrentSymbol() { return currentSymbol; } public void setCurrentSymbol(String value) { this.currentSymbol = value; }
    public String getTerminalReason() { return terminalReason; } public void setTerminalReason(String value) { this.terminalReason = value; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getStartedAt() { return startedAt; } public void setStartedAt(LocalDateTime value) { this.startedAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; } public void setCompletedAt(LocalDateTime value) { this.completedAt = value; }
}
