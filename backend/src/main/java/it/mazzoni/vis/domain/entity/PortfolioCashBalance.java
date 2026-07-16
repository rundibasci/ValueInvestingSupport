package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "portfolio_cash_balance", uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "currency"}))
public class PortfolioCashBalance {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal nativeAmount;
    @Column(nullable = false, length = 3)
    private String baseCurrency;
    @Column(precision = 20, scale = 4)
    private BigDecimal baseAmount;
    private UUID sourceImportId;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
    public UUID getId() { return id; }
    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getNativeAmount() { return nativeAmount; }
    public void setNativeAmount(BigDecimal nativeAmount) { this.nativeAmount = nativeAmount; }
    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public void setBaseAmount(BigDecimal baseAmount) { this.baseAmount = baseAmount; }
    public UUID getSourceImportId() { return sourceImportId; }
    public void setSourceImportId(UUID sourceImportId) { this.sourceImportId = sourceImportId; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
