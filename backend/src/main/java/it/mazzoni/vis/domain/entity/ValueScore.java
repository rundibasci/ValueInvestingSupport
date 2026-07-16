package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "value_score")
public class ValueScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Security security;

    @Column(nullable = false)
    private LocalDate scoreDate;

    @Column(precision = 5, scale = 2)
    private BigDecimal mosScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal qualityScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal safetyScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal growthScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal dividendScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal rawTotalScore;

    @Column(nullable = false)
    private boolean mosGateApplied;

    @Column(length = 50)
    private String weightProfile;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public LocalDate getScoreDate() { return scoreDate; }
    public void setScoreDate(LocalDate scoreDate) { this.scoreDate = scoreDate; }

    public BigDecimal getMosScore() { return mosScore; }
    public void setMosScore(BigDecimal mosScore) { this.mosScore = mosScore; }

    public BigDecimal getQualityScore() { return qualityScore; }
    public void setQualityScore(BigDecimal qualityScore) { this.qualityScore = qualityScore; }

    public BigDecimal getSafetyScore() { return safetyScore; }
    public void setSafetyScore(BigDecimal safetyScore) { this.safetyScore = safetyScore; }

    public BigDecimal getGrowthScore() { return growthScore; }
    public void setGrowthScore(BigDecimal growthScore) { this.growthScore = growthScore; }

    public BigDecimal getDividendScore() { return dividendScore; }
    public void setDividendScore(BigDecimal dividendScore) { this.dividendScore = dividendScore; }

    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }

    public BigDecimal getRawTotalScore() { return rawTotalScore; }
    public void setRawTotalScore(BigDecimal rawTotalScore) { this.rawTotalScore = rawTotalScore; }

    public boolean isMosGateApplied() { return mosGateApplied; }
    public void setMosGateApplied(boolean mosGateApplied) { this.mosGateApplied = mosGateApplied; }

    public String getWeightProfile() { return weightProfile; }
    public void setWeightProfile(String weightProfile) { this.weightProfile = weightProfile; }
}
