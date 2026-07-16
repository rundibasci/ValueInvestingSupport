package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "moat_result")
public class MoatResult {
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
    private MoatStrength moatStrength;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoicTrend roicTrend;

    private Integer yearsAnalyzed;
    private Integer yearsRoicAboveWacc;

    @Column(precision = 10, scale = 4)
    private BigDecimal roicConsistencyPercentage;

    @Column(precision = 10, scale = 4)
    private BigDecimal averageRoic;

    @Column(precision = 10, scale = 4)
    private BigDecimal estimatedWacc;

    @Column(precision = 10, scale = 4)
    private BigDecimal averageRoicSpread;

    @Column(precision = 10, scale = 4)
    private BigDecimal trendSlope;

    @Column(precision = 10, scale = 4)
    private BigDecimal reinvestmentRate;

    @Column(length = 255)
    private String availabilityMessage;

    public UUID getId() { return id; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public LocalDate getResultDate() { return resultDate; }
    public void setResultDate(LocalDate resultDate) { this.resultDate = resultDate; }
    public MoatStrength getMoatStrength() { return moatStrength; }
    public void setMoatStrength(MoatStrength moatStrength) { this.moatStrength = moatStrength; }
    public RoicTrend getRoicTrend() { return roicTrend; }
    public void setRoicTrend(RoicTrend roicTrend) { this.roicTrend = roicTrend; }
    public Integer getYearsAnalyzed() { return yearsAnalyzed; }
    public void setYearsAnalyzed(Integer yearsAnalyzed) { this.yearsAnalyzed = yearsAnalyzed; }
    public Integer getYearsRoicAboveWacc() { return yearsRoicAboveWacc; }
    public void setYearsRoicAboveWacc(Integer yearsRoicAboveWacc) { this.yearsRoicAboveWacc = yearsRoicAboveWacc; }
    public BigDecimal getRoicConsistencyPercentage() { return roicConsistencyPercentage; }
    public void setRoicConsistencyPercentage(BigDecimal roicConsistencyPercentage) { this.roicConsistencyPercentage = roicConsistencyPercentage; }
    public BigDecimal getAverageRoic() { return averageRoic; }
    public void setAverageRoic(BigDecimal averageRoic) { this.averageRoic = averageRoic; }
    public BigDecimal getEstimatedWacc() { return estimatedWacc; }
    public void setEstimatedWacc(BigDecimal estimatedWacc) { this.estimatedWacc = estimatedWacc; }
    public BigDecimal getAverageRoicSpread() { return averageRoicSpread; }
    public void setAverageRoicSpread(BigDecimal averageRoicSpread) { this.averageRoicSpread = averageRoicSpread; }
    public BigDecimal getTrendSlope() { return trendSlope; }
    public void setTrendSlope(BigDecimal trendSlope) { this.trendSlope = trendSlope; }
    public BigDecimal getReinvestmentRate() { return reinvestmentRate; }
    public void setReinvestmentRate(BigDecimal reinvestmentRate) { this.reinvestmentRate = reinvestmentRate; }
    public String getAvailabilityMessage() { return availabilityMessage; }
    public void setAvailabilityMessage(String availabilityMessage) { this.availabilityMessage = availabilityMessage; }
}
