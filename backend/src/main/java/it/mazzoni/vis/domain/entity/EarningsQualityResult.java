package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "earnings_quality_result")
public class EarningsQualityResult {
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
    private BigDecimal fcfToNetIncome;

    @Column(precision = 10, scale = 4)
    private BigDecimal sloanAccrualsRatio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EarningsQualityClassification classification;

    @Column(nullable = false)
    private boolean deteriorating;

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
    public BigDecimal getFcfToNetIncome() { return fcfToNetIncome; }
    public void setFcfToNetIncome(BigDecimal fcfToNetIncome) { this.fcfToNetIncome = fcfToNetIncome; }
    public BigDecimal getSloanAccrualsRatio() { return sloanAccrualsRatio; }
    public void setSloanAccrualsRatio(BigDecimal sloanAccrualsRatio) { this.sloanAccrualsRatio = sloanAccrualsRatio; }
    public EarningsQualityClassification getClassification() { return classification; }
    public void setClassification(EarningsQualityClassification classification) { this.classification = classification; }
    public boolean isDeteriorating() { return deteriorating; }
    public void setDeteriorating(boolean deteriorating) { this.deteriorating = deteriorating; }
    public int getYearsAnalyzed() { return yearsAnalyzed; }
    public void setYearsAnalyzed(int yearsAnalyzed) { this.yearsAnalyzed = yearsAnalyzed; }
    public RiskAvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(RiskAvailabilityStatus availabilityStatus) { this.availabilityStatus = availabilityStatus; }
    public String getAvailabilityMessage() { return availabilityMessage; }
    public void setAvailabilityMessage(String availabilityMessage) { this.availabilityMessage = availabilityMessage; }
}
