package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wacc_result")
public class WaccResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "valuation_result_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ValuationResult valuationResult;

    @Column(precision = 10, scale = 6)
    private BigDecimal wacc;

    @Column(precision = 10, scale = 6)
    private BigDecimal riskFreeRate;

    @Column(precision = 10, scale = 6)
    private BigDecimal equityRiskPremium;

    @Column(precision = 10, scale = 6)
    private BigDecimal beta;

    @Column(precision = 10, scale = 6)
    private BigDecimal costOfEquity;

    @Column(precision = 10, scale = 6)
    private BigDecimal costOfDebt;

    @Column(precision = 10, scale = 6)
    private BigDecimal debtWeight;

    @Column(precision = 10, scale = 6)
    private BigDecimal equityWeight;

    @Column(precision = 10, scale = 6)
    private BigDecimal effectiveTaxRate;

    @Column(nullable = false)
    private boolean fallbackUsed;

    @Column(length = 50)
    private String source;

    public UUID getId() { return id; }
    public ValuationResult getValuationResult() { return valuationResult; }
    public void setValuationResult(ValuationResult valuationResult) { this.valuationResult = valuationResult; }
    public BigDecimal getWacc() { return wacc; }
    public void setWacc(BigDecimal wacc) { this.wacc = wacc; }
    public BigDecimal getRiskFreeRate() { return riskFreeRate; }
    public void setRiskFreeRate(BigDecimal riskFreeRate) { this.riskFreeRate = riskFreeRate; }
    public BigDecimal getEquityRiskPremium() { return equityRiskPremium; }
    public void setEquityRiskPremium(BigDecimal equityRiskPremium) { this.equityRiskPremium = equityRiskPremium; }
    public BigDecimal getBeta() { return beta; }
    public void setBeta(BigDecimal beta) { this.beta = beta; }
    public BigDecimal getCostOfEquity() { return costOfEquity; }
    public void setCostOfEquity(BigDecimal costOfEquity) { this.costOfEquity = costOfEquity; }
    public BigDecimal getCostOfDebt() { return costOfDebt; }
    public void setCostOfDebt(BigDecimal costOfDebt) { this.costOfDebt = costOfDebt; }
    public BigDecimal getDebtWeight() { return debtWeight; }
    public void setDebtWeight(BigDecimal debtWeight) { this.debtWeight = debtWeight; }
    public BigDecimal getEquityWeight() { return equityWeight; }
    public void setEquityWeight(BigDecimal equityWeight) { this.equityWeight = equityWeight; }
    public BigDecimal getEffectiveTaxRate() { return effectiveTaxRate; }
    public void setEffectiveTaxRate(BigDecimal effectiveTaxRate) { this.effectiveTaxRate = effectiveTaxRate; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
