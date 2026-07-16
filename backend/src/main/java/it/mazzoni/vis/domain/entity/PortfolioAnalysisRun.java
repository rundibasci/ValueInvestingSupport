package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "portfolio_analysis_run")
public class PortfolioAnalysisRun {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "portfolio_id") private Portfolio portfolio;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "import_id") private PortfolioImport portfolioImport;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "retry_of_id") private PortfolioAnalysisRun retryOf;
    @Column(nullable = false, length = 64) private String requestFingerprint;
    @Column(nullable = false, length = 40) private String analysisVersion;
    @Column(nullable = false) private String symbols;
    @Column(nullable = false, columnDefinition = "TEXT") private String inputSnapshot;
    @Column(nullable = false, length = 30) private String status;
    @Column(nullable = false, length = 30) private String phase;
    @Column(nullable = false) private int totalCount;
    @Column(nullable = false) private int processedCount;
    @Column(nullable = false) private int succeededCount;
    @Column(nullable = false) private int partialCount;
    @Column(nullable = false) private int failedCount;
    @Column(length = 20) private String currentSymbol;
    @Column(length = 500) private String terminalReason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "analytics_snapshot_id") private PortfolioAnalyticsSnapshot analyticsSnapshot;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    public UUID getId(){return id;} public void setId(UUID v){id=v;}
    public User getUser(){return user;} public void setUser(User v){user=v;}
    public Portfolio getPortfolio(){return portfolio;} public void setPortfolio(Portfolio v){portfolio=v;}
    public PortfolioImport getPortfolioImport(){return portfolioImport;} public void setPortfolioImport(PortfolioImport v){portfolioImport=v;}
    public PortfolioAnalysisRun getRetryOf(){return retryOf;} public void setRetryOf(PortfolioAnalysisRun v){retryOf=v;}
    public String getRequestFingerprint(){return requestFingerprint;} public void setRequestFingerprint(String v){requestFingerprint=v;}
    public String getAnalysisVersion(){return analysisVersion;} public void setAnalysisVersion(String v){analysisVersion=v;}
    public String getSymbols(){return symbols;} public void setSymbols(String v){symbols=v;}
    public String getInputSnapshot(){return inputSnapshot;} public void setInputSnapshot(String v){inputSnapshot=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getPhase(){return phase;} public void setPhase(String v){phase=v;}
    public int getTotalCount(){return totalCount;} public void setTotalCount(int v){totalCount=v;}
    public int getProcessedCount(){return processedCount;} public void setProcessedCount(int v){processedCount=v;}
    public int getSucceededCount(){return succeededCount;} public void setSucceededCount(int v){succeededCount=v;}
    public int getPartialCount(){return partialCount;} public void setPartialCount(int v){partialCount=v;}
    public int getFailedCount(){return failedCount;} public void setFailedCount(int v){failedCount=v;}
    public String getCurrentSymbol(){return currentSymbol;} public void setCurrentSymbol(String v){currentSymbol=v;}
    public String getTerminalReason(){return terminalReason;} public void setTerminalReason(String v){terminalReason=v;}
    public PortfolioAnalyticsSnapshot getAnalyticsSnapshot(){return analyticsSnapshot;} public void setAnalyticsSnapshot(PortfolioAnalyticsSnapshot v){analyticsSnapshot=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getStartedAt(){return startedAt;} public void setStartedAt(LocalDateTime v){startedAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
    public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime v){completedAt=v;}
}
