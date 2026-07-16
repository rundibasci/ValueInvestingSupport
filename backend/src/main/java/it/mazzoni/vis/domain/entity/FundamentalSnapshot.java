package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fundamental_snapshot")
public class FundamentalSnapshot {

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

    private Integer fiscalYear;
    private Integer fiscalQuarter;
    private LocalDate reportDate;

    @Column(precision = 20, scale = 2)
    private BigDecimal revenue;

    @Column(precision = 20, scale = 2)
    private BigDecimal netIncome;

    @Column(precision = 20, scale = 2)
    private BigDecimal operatingIncome;

    @Column(precision = 20, scale = 2)
    private BigDecimal grossProfit;

    @Column(precision = 10, scale = 4)
    private BigDecimal eps;

    @Column(precision = 10, scale = 4)
    private BigDecimal epsDiluted;

    @Column(precision = 20, scale = 2)
    private BigDecimal freeCashFlow;

    @Column(precision = 20, scale = 2)
    private BigDecimal operatingCashFlow;

    @Column(precision = 20, scale = 2)
    private BigDecimal totalAssets;

    @Column(precision = 20, scale = 2)
    private BigDecimal totalLiabilities;

    @Column(precision = 20, scale = 2)
    private BigDecimal totalEquity;

    @Column(precision = 20, scale = 2)
    private BigDecimal totalDebt;

    @Column(precision = 20, scale = 2)
    private BigDecimal cash;

    private Long sharesOutstanding;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public Period getPeriod() { return period; }
    public void setPeriod(Period period) { this.period = period; }

    public Integer getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(Integer fiscalYear) { this.fiscalYear = fiscalYear; }

    public Integer getFiscalQuarter() { return fiscalQuarter; }
    public void setFiscalQuarter(Integer fiscalQuarter) { this.fiscalQuarter = fiscalQuarter; }

    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public BigDecimal getNetIncome() { return netIncome; }
    public void setNetIncome(BigDecimal netIncome) { this.netIncome = netIncome; }

    public BigDecimal getOperatingIncome() { return operatingIncome; }
    public void setOperatingIncome(BigDecimal operatingIncome) { this.operatingIncome = operatingIncome; }

    public BigDecimal getGrossProfit() { return grossProfit; }
    public void setGrossProfit(BigDecimal grossProfit) { this.grossProfit = grossProfit; }

    public BigDecimal getEps() { return eps; }
    public void setEps(BigDecimal eps) { this.eps = eps; }

    public BigDecimal getEpsDiluted() { return epsDiluted; }
    public void setEpsDiluted(BigDecimal epsDiluted) { this.epsDiluted = epsDiluted; }

    public BigDecimal getFreeCashFlow() { return freeCashFlow; }
    public void setFreeCashFlow(BigDecimal freeCashFlow) { this.freeCashFlow = freeCashFlow; }

    public BigDecimal getOperatingCashFlow() { return operatingCashFlow; }
    public void setOperatingCashFlow(BigDecimal operatingCashFlow) { this.operatingCashFlow = operatingCashFlow; }

    public BigDecimal getTotalAssets() { return totalAssets; }
    public void setTotalAssets(BigDecimal totalAssets) { this.totalAssets = totalAssets; }

    public BigDecimal getTotalLiabilities() { return totalLiabilities; }
    public void setTotalLiabilities(BigDecimal totalLiabilities) { this.totalLiabilities = totalLiabilities; }

    public BigDecimal getTotalEquity() { return totalEquity; }
    public void setTotalEquity(BigDecimal totalEquity) { this.totalEquity = totalEquity; }

    public BigDecimal getTotalDebt() { return totalDebt; }
    public void setTotalDebt(BigDecimal totalDebt) { this.totalDebt = totalDebt; }

    public BigDecimal getCash() { return cash; }
    public void setCash(BigDecimal cash) { this.cash = cash; }

    public Long getSharesOutstanding() { return sharesOutstanding; }
    public void setSharesOutstanding(Long sharesOutstanding) { this.sharesOutstanding = sharesOutstanding; }
}
