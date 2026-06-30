package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "piotroski_result")
public class PiotroskiResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(nullable = false)
    private LocalDate resultDate;

    @Column(nullable = false)
    private int totalScore;

    @Column(nullable = false)
    private boolean positiveNetIncome;
    @Column(nullable = false)
    private boolean positiveOperatingCashFlow;
    @Column(nullable = false)
    private boolean improvingRoa;
    @Column(nullable = false)
    private boolean cashFlowQuality;
    @Column(nullable = false)
    private boolean lowerLeverage;
    @Column(nullable = false)
    private boolean improvingCurrentRatio;
    @Column(nullable = false)
    private boolean noShareDilution;
    @Column(nullable = false)
    private boolean improvingGrossMargin;
    @Column(nullable = false)
    private boolean improvingAssetTurnover;

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
    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
    public boolean isPositiveNetIncome() { return positiveNetIncome; }
    public void setPositiveNetIncome(boolean positiveNetIncome) { this.positiveNetIncome = positiveNetIncome; }
    public boolean isPositiveOperatingCashFlow() { return positiveOperatingCashFlow; }
    public void setPositiveOperatingCashFlow(boolean positiveOperatingCashFlow) { this.positiveOperatingCashFlow = positiveOperatingCashFlow; }
    public boolean isImprovingRoa() { return improvingRoa; }
    public void setImprovingRoa(boolean improvingRoa) { this.improvingRoa = improvingRoa; }
    public boolean isCashFlowQuality() { return cashFlowQuality; }
    public void setCashFlowQuality(boolean cashFlowQuality) { this.cashFlowQuality = cashFlowQuality; }
    public boolean isLowerLeverage() { return lowerLeverage; }
    public void setLowerLeverage(boolean lowerLeverage) { this.lowerLeverage = lowerLeverage; }
    public boolean isImprovingCurrentRatio() { return improvingCurrentRatio; }
    public void setImprovingCurrentRatio(boolean improvingCurrentRatio) { this.improvingCurrentRatio = improvingCurrentRatio; }
    public boolean isNoShareDilution() { return noShareDilution; }
    public void setNoShareDilution(boolean noShareDilution) { this.noShareDilution = noShareDilution; }
    public boolean isImprovingGrossMargin() { return improvingGrossMargin; }
    public void setImprovingGrossMargin(boolean improvingGrossMargin) { this.improvingGrossMargin = improvingGrossMargin; }
    public boolean isImprovingAssetTurnover() { return improvingAssetTurnover; }
    public void setImprovingAssetTurnover(boolean improvingAssetTurnover) { this.improvingAssetTurnover = improvingAssetTurnover; }
    public RiskAvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(RiskAvailabilityStatus availabilityStatus) { this.availabilityStatus = availabilityStatus; }
    public String getAvailabilityMessage() { return availabilityMessage; }
    public void setAvailabilityMessage(String availabilityMessage) { this.availabilityMessage = availabilityMessage; }
}
