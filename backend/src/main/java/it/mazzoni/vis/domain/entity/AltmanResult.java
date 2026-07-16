package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "altman_result")
public class AltmanResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Security security;

    @Column(nullable = false)
    private LocalDate resultDate;

    @Column(precision = 10, scale = 4)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AltmanZone zone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AltmanFormulaVariant formulaVariant;

    @Column(precision = 10, scale = 4)
    private BigDecimal workingCapitalToAssets;
    @Column(precision = 10, scale = 4)
    private BigDecimal retainedEarningsToAssets;
    @Column(precision = 10, scale = 4)
    private BigDecimal ebitToAssets;
    @Column(precision = 10, scale = 4)
    private BigDecimal marketValueEquityToLiabilities;
    @Column(precision = 10, scale = 4)
    private BigDecimal salesToAssets;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RiskAvailabilityStatus availabilityStatus;

    @Column(length = 255)
    private String availabilityMessage;

    public UUID getId() { return id; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public LocalDate getResultDate() { return resultDate; }
    public void setResultDate(LocalDate resultDate) { this.resultDate = resultDate; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public AltmanZone getZone() { return zone; }
    public void setZone(AltmanZone zone) { this.zone = zone; }
    public AltmanFormulaVariant getFormulaVariant() { return formulaVariant; }
    public void setFormulaVariant(AltmanFormulaVariant formulaVariant) { this.formulaVariant = formulaVariant; }
    public BigDecimal getWorkingCapitalToAssets() { return workingCapitalToAssets; }
    public void setWorkingCapitalToAssets(BigDecimal workingCapitalToAssets) { this.workingCapitalToAssets = workingCapitalToAssets; }
    public BigDecimal getRetainedEarningsToAssets() { return retainedEarningsToAssets; }
    public void setRetainedEarningsToAssets(BigDecimal retainedEarningsToAssets) { this.retainedEarningsToAssets = retainedEarningsToAssets; }
    public BigDecimal getEbitToAssets() { return ebitToAssets; }
    public void setEbitToAssets(BigDecimal ebitToAssets) { this.ebitToAssets = ebitToAssets; }
    public BigDecimal getMarketValueEquityToLiabilities() { return marketValueEquityToLiabilities; }
    public void setMarketValueEquityToLiabilities(BigDecimal marketValueEquityToLiabilities) { this.marketValueEquityToLiabilities = marketValueEquityToLiabilities; }
    public BigDecimal getSalesToAssets() { return salesToAssets; }
    public void setSalesToAssets(BigDecimal salesToAssets) { this.salesToAssets = salesToAssets; }
    public RiskAvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(RiskAvailabilityStatus availabilityStatus) { this.availabilityStatus = availabilityStatus; }
    public String getAvailabilityMessage() { return availabilityMessage; }
    public void setAvailabilityMessage(String availabilityMessage) { this.availabilityMessage = availabilityMessage; }
}
