package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "portfolio_analytics_snapshot")
public class PortfolioAnalyticsSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false)
    private LocalDateTime capturedAt;

    @Column(precision = 20, scale = 4)
    private BigDecimal totalMarketValue;

    @Column(nullable = false, length = 20)
    private String benchmarkSymbol;

    @Column(nullable = false)
    private int warningCount;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @PrePersist
    void prePersist() {
        if (capturedAt == null) {
            capturedAt = LocalDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime capturedAt) { this.capturedAt = capturedAt; }
    public BigDecimal getTotalMarketValue() { return totalMarketValue; }
    public void setTotalMarketValue(BigDecimal totalMarketValue) { this.totalMarketValue = totalMarketValue; }
    public String getBenchmarkSymbol() { return benchmarkSymbol; }
    public void setBenchmarkSymbol(String benchmarkSymbol) { this.benchmarkSymbol = benchmarkSymbol; }
    public int getWarningCount() { return warningCount; }
    public void setWarningCount(int warningCount) { this.warningCount = warningCount; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
