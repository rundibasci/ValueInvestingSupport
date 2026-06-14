package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
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
    private Security security;

    @Column(nullable = false)
    private LocalDate valuationDate;

    @Column(precision = 15, scale = 4)
    private BigDecimal dcfFairValue;

    @Column(precision = 15, scale = 4)
    private BigDecimal dcfFairValueLow;

    @Column(precision = 15, scale = 4)
    private BigDecimal dcfFairValueHigh;

    @Column(precision = 15, scale = 4)
    private BigDecimal grahamNumber;

    @Column(precision = 15, scale = 4)
    private BigDecimal ddmFairValue;

    @Column(precision = 15, scale = 4)
    private BigDecimal compositeFairValue;

    @Column(precision = 15, scale = 4)
    private BigDecimal currentPrice;

    @Column(precision = 10, scale = 4)
    private BigDecimal marginOfSafety;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Recommendation recommendation;

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

    public BigDecimal getGrahamNumber() { return grahamNumber; }
    public void setGrahamNumber(BigDecimal grahamNumber) { this.grahamNumber = grahamNumber; }

    public BigDecimal getDdmFairValue() { return ddmFairValue; }
    public void setDdmFairValue(BigDecimal ddmFairValue) { this.ddmFairValue = ddmFairValue; }

    public BigDecimal getCompositeFairValue() { return compositeFairValue; }
    public void setCompositeFairValue(BigDecimal compositeFairValue) { this.compositeFairValue = compositeFairValue; }

    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

    public BigDecimal getMarginOfSafety() { return marginOfSafety; }
    public void setMarginOfSafety(BigDecimal marginOfSafety) { this.marginOfSafety = marginOfSafety; }

    public Recommendation getRecommendation() { return recommendation; }
    public void setRecommendation(Recommendation recommendation) { this.recommendation = recommendation; }
}
