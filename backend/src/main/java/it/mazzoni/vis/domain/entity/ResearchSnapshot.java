package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "research_snapshot")
public class ResearchSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_id")
    private Security security;
    @Column(nullable = false, length = 20)
    private String symbol;
    @Column(nullable = false, length = 40)
    private String actionType;
    @Column(nullable = false, updatable = false)
    private LocalDateTime capturedAt;
    @Column(precision = 15, scale = 4)
    private BigDecimal currentPrice;
    @Column(precision = 15, scale = 4)
    private BigDecimal compositeFairValue;
    @Column(precision = 10, scale = 4)
    private BigDecimal marginOfSafety;
    @Column(precision = 5, scale = 2)
    private BigDecimal valueScore;
    @Column(precision = 10, scale = 4)
    private BigDecimal waccUsed;
    @Column(length = 30)
    private String dataSource;
    private Integer piotroskiScore;
    @Column(length = 30)
    private String moatClassification;
    @Column(columnDefinition = "TEXT")
    private String rationale;

    @PrePersist
    void onCreate() {
        if (capturedAt == null) capturedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime capturedAt) { this.capturedAt = capturedAt; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public BigDecimal getCompositeFairValue() { return compositeFairValue; }
    public void setCompositeFairValue(BigDecimal compositeFairValue) { this.compositeFairValue = compositeFairValue; }
    public BigDecimal getMarginOfSafety() { return marginOfSafety; }
    public void setMarginOfSafety(BigDecimal marginOfSafety) { this.marginOfSafety = marginOfSafety; }
    public BigDecimal getValueScore() { return valueScore; }
    public void setValueScore(BigDecimal valueScore) { this.valueScore = valueScore; }
    public BigDecimal getWaccUsed() { return waccUsed; }
    public void setWaccUsed(BigDecimal waccUsed) { this.waccUsed = waccUsed; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public Integer getPiotroskiScore() { return piotroskiScore; }
    public void setPiotroskiScore(Integer piotroskiScore) { this.piotroskiScore = piotroskiScore; }
    public String getMoatClassification() { return moatClassification; }
    public void setMoatClassification(String moatClassification) { this.moatClassification = moatClassification; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
}
