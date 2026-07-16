package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cyclicality_result")
public class CyclicalityResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Security security;

    @Column(nullable = false)
    private LocalDate resultDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CyclicalityClassification classification;

    @Column(precision = 10, scale = 4)
    private BigDecimal revenueCoefficient;
    @Column(precision = 10, scale = 4)
    private BigDecimal earningsCoefficient;
    @Column(precision = 20, scale = 2)
    private BigDecimal normalizedEarnings;
    @Column(precision = 10, scale = 4)
    private BigDecimal cycleAdjustedPe;

    @Column(nullable = false)
    private int yearsAnalyzed;

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
    public CyclicalityClassification getClassification() { return classification; }
    public void setClassification(CyclicalityClassification classification) { this.classification = classification; }
    public BigDecimal getRevenueCoefficient() { return revenueCoefficient; }
    public void setRevenueCoefficient(BigDecimal revenueCoefficient) { this.revenueCoefficient = revenueCoefficient; }
    public BigDecimal getEarningsCoefficient() { return earningsCoefficient; }
    public void setEarningsCoefficient(BigDecimal earningsCoefficient) { this.earningsCoefficient = earningsCoefficient; }
    public BigDecimal getNormalizedEarnings() { return normalizedEarnings; }
    public void setNormalizedEarnings(BigDecimal normalizedEarnings) { this.normalizedEarnings = normalizedEarnings; }
    public BigDecimal getCycleAdjustedPe() { return cycleAdjustedPe; }
    public void setCycleAdjustedPe(BigDecimal cycleAdjustedPe) { this.cycleAdjustedPe = cycleAdjustedPe; }
    public int getYearsAnalyzed() { return yearsAnalyzed; }
    public void setYearsAnalyzed(int yearsAnalyzed) { this.yearsAnalyzed = yearsAnalyzed; }
    public RiskAvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(RiskAvailabilityStatus availabilityStatus) { this.availabilityStatus = availabilityStatus; }
    public String getAvailabilityMessage() { return availabilityMessage; }
    public void setAvailabilityMessage(String availabilityMessage) { this.availabilityMessage = availabilityMessage; }
}
