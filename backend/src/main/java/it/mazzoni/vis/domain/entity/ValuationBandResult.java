package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "valuation_band_result")
public class ValuationBandResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Security security;

    @Column(nullable = false)
    private LocalDate resultDate;

    @Column(nullable = false, length = 30)
    private String metric;

    private Integer yearsAnalyzed;

    @Column(precision = 10, scale = 4)
    private BigDecimal currentValue;
    @Column(precision = 10, scale = 4)
    private BigDecimal medianValue;
    @Column(precision = 10, scale = 4)
    private BigDecimal percentile25;
    @Column(precision = 10, scale = 4)
    private BigDecimal percentile75;
    @Column(precision = 10, scale = 4)
    private BigDecimal currentPercentile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ValuationBandPosition position;

    @Column(length = 255)
    private String availabilityMessage;

    public UUID getId() { return id; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public LocalDate getResultDate() { return resultDate; }
    public void setResultDate(LocalDate resultDate) { this.resultDate = resultDate; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public Integer getYearsAnalyzed() { return yearsAnalyzed; }
    public void setYearsAnalyzed(Integer yearsAnalyzed) { this.yearsAnalyzed = yearsAnalyzed; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }
    public BigDecimal getMedianValue() { return medianValue; }
    public void setMedianValue(BigDecimal medianValue) { this.medianValue = medianValue; }
    public BigDecimal getPercentile25() { return percentile25; }
    public void setPercentile25(BigDecimal percentile25) { this.percentile25 = percentile25; }
    public BigDecimal getPercentile75() { return percentile75; }
    public void setPercentile75(BigDecimal percentile75) { this.percentile75 = percentile75; }
    public BigDecimal getCurrentPercentile() { return currentPercentile; }
    public void setCurrentPercentile(BigDecimal currentPercentile) { this.currentPercentile = currentPercentile; }
    public ValuationBandPosition getPosition() { return position; }
    public void setPosition(ValuationBandPosition position) { this.position = position; }
    public String getAvailabilityMessage() { return availabilityMessage; }
    public void setAvailabilityMessage(String availabilityMessage) { this.availabilityMessage = availabilityMessage; }
}
