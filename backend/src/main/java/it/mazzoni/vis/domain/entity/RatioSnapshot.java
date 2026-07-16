package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ratio_snapshot")
public class RatioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Security security;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Period period;

    private LocalDate reportDate;

    @Column(precision = 10, scale = 4)
    private BigDecimal peRatio;

    @Column(precision = 10, scale = 4)
    private BigDecimal pbRatio;

    @Column(precision = 10, scale = 4)
    private BigDecimal psRatio;

    @Column(precision = 10, scale = 4)
    private BigDecimal evToEbitda;

    @Column(precision = 10, scale = 4)
    private BigDecimal roic;

    @Column(precision = 10, scale = 4)
    private BigDecimal roe;

    @Column(precision = 10, scale = 4)
    private BigDecimal roa;

    @Column(precision = 10, scale = 4)
    private BigDecimal debtToEquity;

    @Column(precision = 10, scale = 4)
    private BigDecimal currentRatio;

    @Column(precision = 10, scale = 4)
    private BigDecimal quickRatio;

    @Column(precision = 10, scale = 4)
    private BigDecimal interestCoverage;

    @Column(precision = 10, scale = 4)
    private BigDecimal dividendYield;

    @Column(precision = 10, scale = 4)
    private BigDecimal payoutRatio;

    @Column(precision = 10, scale = 4)
    private BigDecimal grossMargin;

    @Column(precision = 10, scale = 4)
    private BigDecimal operatingMargin;

    @Column(precision = 10, scale = 4)
    private BigDecimal netMargin;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public Period getPeriod() { return period; }
    public void setPeriod(Period period) { this.period = period; }

    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }

    public BigDecimal getPeRatio() { return peRatio; }
    public void setPeRatio(BigDecimal peRatio) { this.peRatio = peRatio; }

    public BigDecimal getPbRatio() { return pbRatio; }
    public void setPbRatio(BigDecimal pbRatio) { this.pbRatio = pbRatio; }

    public BigDecimal getPsRatio() { return psRatio; }
    public void setPsRatio(BigDecimal psRatio) { this.psRatio = psRatio; }

    public BigDecimal getEvToEbitda() { return evToEbitda; }
    public void setEvToEbitda(BigDecimal evToEbitda) { this.evToEbitda = evToEbitda; }

    public BigDecimal getRoic() { return roic; }
    public void setRoic(BigDecimal roic) { this.roic = roic; }

    public BigDecimal getRoe() { return roe; }
    public void setRoe(BigDecimal roe) { this.roe = roe; }

    public BigDecimal getRoa() { return roa; }
    public void setRoa(BigDecimal roa) { this.roa = roa; }

    public BigDecimal getDebtToEquity() { return debtToEquity; }
    public void setDebtToEquity(BigDecimal debtToEquity) { this.debtToEquity = debtToEquity; }

    public BigDecimal getCurrentRatio() { return currentRatio; }
    public void setCurrentRatio(BigDecimal currentRatio) { this.currentRatio = currentRatio; }

    public BigDecimal getQuickRatio() { return quickRatio; }
    public void setQuickRatio(BigDecimal quickRatio) { this.quickRatio = quickRatio; }

    public BigDecimal getInterestCoverage() { return interestCoverage; }
    public void setInterestCoverage(BigDecimal interestCoverage) { this.interestCoverage = interestCoverage; }

    public BigDecimal getDividendYield() { return dividendYield; }
    public void setDividendYield(BigDecimal dividendYield) { this.dividendYield = dividendYield; }

    public BigDecimal getPayoutRatio() { return payoutRatio; }
    public void setPayoutRatio(BigDecimal payoutRatio) { this.payoutRatio = payoutRatio; }

    public BigDecimal getGrossMargin() { return grossMargin; }
    public void setGrossMargin(BigDecimal grossMargin) { this.grossMargin = grossMargin; }

    public BigDecimal getOperatingMargin() { return operatingMargin; }
    public void setOperatingMargin(BigDecimal operatingMargin) { this.operatingMargin = operatingMargin; }

    public BigDecimal getNetMargin() { return netMargin; }
    public void setNetMargin(BigDecimal netMargin) { this.netMargin = netMargin; }
}
