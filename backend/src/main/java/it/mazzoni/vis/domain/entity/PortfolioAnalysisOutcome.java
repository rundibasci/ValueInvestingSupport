package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "portfolio_analysis_outcome")
public class PortfolioAnalysisOutcome {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "analysis_run_id") private PortfolioAnalysisRun analysisRun;
    @Column(nullable = false) private int position;
    @Column(nullable = false, length = 20) private String symbol;
    @Column(nullable = false, length = 30) private String status;
    @Column(length = 60) private String source;
    private LocalDate refreshedAt;
    @Column(precision=20,scale=6) private BigDecimal sourceLastPrice;
    @Column(precision=20,scale=4) private BigDecimal sourceBaseValue;
    @Column(precision=20,scale=6) private BigDecimal refreshedPrice;
    @Column(precision=12,scale=4) private BigDecimal priceVariancePercent;
    @Column(length = 80) private String reasonCode;
    @Column(length = 500) private String reason;
    @Column(length = 1000) private String fallbackReason;
    @Column(length = 500) private String errorMessage;
    @Column(length = 200) private String reviewPath;
    @Column(nullable = false, length = 40) private String calculationVersion;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    public UUID getId(){return id;} public void setId(UUID v){id=v;}
    public PortfolioAnalysisRun getAnalysisRun(){return analysisRun;} public void setAnalysisRun(PortfolioAnalysisRun v){analysisRun=v;}
    public int getPosition(){return position;} public void setPosition(int v){position=v;}
    public String getSymbol(){return symbol;} public void setSymbol(String v){symbol=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getSource(){return source;} public void setSource(String v){source=v;}
    public LocalDate getRefreshedAt(){return refreshedAt;} public void setRefreshedAt(LocalDate v){refreshedAt=v;}
    public BigDecimal getSourceLastPrice(){return sourceLastPrice;} public void setSourceLastPrice(BigDecimal v){sourceLastPrice=v;}
    public BigDecimal getSourceBaseValue(){return sourceBaseValue;} public void setSourceBaseValue(BigDecimal v){sourceBaseValue=v;}
    public BigDecimal getRefreshedPrice(){return refreshedPrice;} public void setRefreshedPrice(BigDecimal v){refreshedPrice=v;}
    public BigDecimal getPriceVariancePercent(){return priceVariancePercent;} public void setPriceVariancePercent(BigDecimal v){priceVariancePercent=v;}
    public String getReasonCode(){return reasonCode;} public void setReasonCode(String v){reasonCode=v;}
    public String getReason(){return reason;} public void setReason(String v){reason=v;}
    public String getFallbackReason(){return fallbackReason;} public void setFallbackReason(String v){fallbackReason=v;}
    public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;}
    public String getReviewPath(){return reviewPath;} public void setReviewPath(String v){reviewPath=v;}
    public String getCalculationVersion(){return calculationVersion;} public void setCalculationVersion(String v){calculationVersion=v;}
    public LocalDateTime getStartedAt(){return startedAt;} public void setStartedAt(LocalDateTime v){startedAt=v;}
    public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime v){completedAt=v;}
}
