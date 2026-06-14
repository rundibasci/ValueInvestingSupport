package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "insider_trade")
public class InsiderTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false)
    private String insiderName;

    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType transactionType;

    private Long shares;

    @Column(precision = 15, scale = 4)
    private BigDecimal pricePerShare;

    @Column(precision = 20, scale = 2)
    private BigDecimal tradeValue;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }

    public String getInsiderName() { return insiderName; }
    public void setInsiderName(String insiderName) { this.insiderName = insiderName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }

    public Long getShares() { return shares; }
    public void setShares(Long shares) { this.shares = shares; }

    public BigDecimal getPricePerShare() { return pricePerShare; }
    public void setPricePerShare(BigDecimal pricePerShare) { this.pricePerShare = pricePerShare; }

    public BigDecimal getTradeValue() { return tradeValue; }
    public void setTradeValue(BigDecimal tradeValue) { this.tradeValue = tradeValue; }
}
