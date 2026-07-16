package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "valuation_result")
public class ValuationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Security security;

    @Column(nullable = false)
    private LocalDate valuationDate;

    @Column(precision = 15, scale = 4)
    private BigDecimal dcfFairValue;

    @Column(precision = 15, scale = 4)
    private BigDecimal dcfFairValueLow;

    @Column(precision = 15, scale = 4)
    private BigDecimal dcfFairValueHigh;

    @Column(precision = 10, scale = 4)
    private BigDecimal dcfTerminalValuePercentage;

    @Column(nullable = false)
    private boolean dcfHighTerminalDependence;

    @Column(precision = 15, scale = 4)
    private BigDecimal grahamNumber;

    @Column(precision = 15, scale = 4)
    private BigDecimal ddmFairValue;

    @Column(precision = 15, scale = 4)
    private BigDecimal epvFairValue;

    @Column(precision = 20, scale = 2)
    private BigDecimal epvNormalizedEarnings;

    private Integer epvYearsAveraged;

    @Column(precision = 20, scale = 2)
    private BigDecimal ownerEarnings;

    @Column(precision = 20, scale = 2)
    private BigDecimal maintenanceCapexEstimate;

    @Column(precision = 15, scale = 4)
    private BigDecimal compositeFairValue;

    @Column(precision = 15, scale = 4)
    private BigDecimal currentPrice;

    @Column(precision = 10, scale = 4)
    private BigDecimal marginOfSafety;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Recommendation recommendation;

    @Column(length = 30)
    private String source;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public LocalDate getValuationDate() { return valuationDate; }
    public void setValuationDate(LocalDate valuationDate) { this.valuationDate = valuationDate; }

    public BigDecimal getDcfFairValue() { return dcfFairValue; }
    public void setDcfFairValue(BigDecimal dcfFairValue) { this.dcfFairValue = dcfFairValue; }

    public BigDecimal getDcfFairValueLow() { return dcfFairValueLow; }
    public void setDcfFairValueLow(BigDecimal dcfFairValueLow) { this.dcfFairValueLow = dcfFairValueLow; }

    public BigDecimal getDcfFairValueHigh() { return dcfFairValueHigh; }
    public void setDcfFairValueHigh(BigDecimal dcfFairValueHigh) { this.dcfFairValueHigh = dcfFairValueHigh; }

    public BigDecimal getDcfTerminalValuePercentage() { return dcfTerminalValuePercentage; }
    public void setDcfTerminalValuePercentage(BigDecimal dcfTerminalValuePercentage) {
        this.dcfTerminalValuePercentage = dcfTerminalValuePercentage;
    }

    public boolean isDcfHighTerminalDependence() { return dcfHighTerminalDependence; }
    public void setDcfHighTerminalDependence(boolean dcfHighTerminalDependence) {
        this.dcfHighTerminalDependence = dcfHighTerminalDependence;
    }

    public BigDecimal getGrahamNumber() { return grahamNumber; }
    public void setGrahamNumber(BigDecimal grahamNumber) { this.grahamNumber = grahamNumber; }

    public BigDecimal getDdmFairValue() { return ddmFairValue; }
    public void setDdmFairValue(BigDecimal ddmFairValue) { this.ddmFairValue = ddmFairValue; }

    public BigDecimal getEpvFairValue() { return epvFairValue; }
    public void setEpvFairValue(BigDecimal epvFairValue) { this.epvFairValue = epvFairValue; }

    public BigDecimal getEpvNormalizedEarnings() { return epvNormalizedEarnings; }
    public void setEpvNormalizedEarnings(BigDecimal epvNormalizedEarnings) {
        this.epvNormalizedEarnings = epvNormalizedEarnings;
    }

    public Integer getEpvYearsAveraged() { return epvYearsAveraged; }
    public void setEpvYearsAveraged(Integer epvYearsAveraged) { this.epvYearsAveraged = epvYearsAveraged; }

    public BigDecimal getOwnerEarnings() { return ownerEarnings; }
    public void setOwnerEarnings(BigDecimal ownerEarnings) { this.ownerEarnings = ownerEarnings; }

    public BigDecimal getMaintenanceCapexEstimate() { return maintenanceCapexEstimate; }
    public void setMaintenanceCapexEstimate(BigDecimal maintenanceCapexEstimate) {
        this.maintenanceCapexEstimate = maintenanceCapexEstimate;
    }

    public BigDecimal getCompositeFairValue() { return compositeFairValue; }
    public void setCompositeFairValue(BigDecimal compositeFairValue) { this.compositeFairValue = compositeFairValue; }

    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

    public BigDecimal getMarginOfSafety() { return marginOfSafety; }
    public void setMarginOfSafety(BigDecimal marginOfSafety) { this.marginOfSafety = marginOfSafety; }

    public Recommendation getRecommendation() { return recommendation; }
    public void setRecommendation(Recommendation recommendation) { this.recommendation = recommendation; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
