package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "capital_allocation_result")
public class CapitalAllocationResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(nullable = false)
    private LocalDate resultDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SharesOutstandingTrend sharesOutstandingTrend;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CapitalAllocatorClassification classification;

    private Integer yearsAnalyzed;

    @Column(precision = 10, scale = 4)
    private BigDecimal sharesChangePercentage;

    @Column(precision = 10, scale = 4)
    private BigDecimal sharesCagr;

    @Column(precision = 10, scale = 4)
    private BigDecimal dividendYield;

    @Column(precision = 10, scale = 4)
    private BigDecimal netBuybackYield;

    @Column(precision = 10, scale = 4)
    private BigDecimal totalShareholderYield;

    @Column(precision = 10, scale = 4)
    private BigDecimal insiderOwnershipPercentage;

    @Column(precision = 10, scale = 4)
    private BigDecimal acquisitionSpendToFcf;

    @Column(length = 255)
    private String availabilityMessage;

    public UUID getId() { return id; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public LocalDate getResultDate() { return resultDate; }
    public void setResultDate(LocalDate resultDate) { this.resultDate = resultDate; }
    public SharesOutstandingTrend getSharesOutstandingTrend() { return sharesOutstandingTrend; }
    public void setSharesOutstandingTrend(SharesOutstandingTrend sharesOutstandingTrend) { this.sharesOutstandingTrend = sharesOutstandingTrend; }
    public CapitalAllocatorClassification getClassification() { return classification; }
    public void setClassification(CapitalAllocatorClassification classification) { this.classification = classification; }
    public Integer getYearsAnalyzed() { return yearsAnalyzed; }
    public void setYearsAnalyzed(Integer yearsAnalyzed) { this.yearsAnalyzed = yearsAnalyzed; }
    public BigDecimal getSharesChangePercentage() { return sharesChangePercentage; }
    public void setSharesChangePercentage(BigDecimal sharesChangePercentage) { this.sharesChangePercentage = sharesChangePercentage; }
    public BigDecimal getSharesCagr() { return sharesCagr; }
    public void setSharesCagr(BigDecimal sharesCagr) { this.sharesCagr = sharesCagr; }
    public BigDecimal getDividendYield() { return dividendYield; }
    public void setDividendYield(BigDecimal dividendYield) { this.dividendYield = dividendYield; }
    public BigDecimal getNetBuybackYield() { return netBuybackYield; }
    public void setNetBuybackYield(BigDecimal netBuybackYield) { this.netBuybackYield = netBuybackYield; }
    public BigDecimal getTotalShareholderYield() { return totalShareholderYield; }
    public void setTotalShareholderYield(BigDecimal totalShareholderYield) { this.totalShareholderYield = totalShareholderYield; }
    public BigDecimal getInsiderOwnershipPercentage() { return insiderOwnershipPercentage; }
    public void setInsiderOwnershipPercentage(BigDecimal insiderOwnershipPercentage) { this.insiderOwnershipPercentage = insiderOwnershipPercentage; }
    public BigDecimal getAcquisitionSpendToFcf() { return acquisitionSpendToFcf; }
    public void setAcquisitionSpendToFcf(BigDecimal acquisitionSpendToFcf) { this.acquisitionSpendToFcf = acquisitionSpendToFcf; }
    public String getAvailabilityMessage() { return availabilityMessage; }
    public void setAvailabilityMessage(String availabilityMessage) { this.availabilityMessage = availabilityMessage; }
}
