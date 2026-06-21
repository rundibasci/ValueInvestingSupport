package it.mazzoni.vis.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "rebalance_line")
public class RebalanceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id")
    private RebalanceProposal proposal;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private BigDecimal capturedPrice;

    @Column(nullable = false)
    private BigDecimal currentQuantity;

    @Column(nullable = false)
    private BigDecimal targetQuantity;

    public UUID getId() { return id; }
    public RebalanceProposal getProposal() { return proposal; }
    public void setProposal(RebalanceProposal proposal) { this.proposal = proposal; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public BigDecimal getCapturedPrice() { return capturedPrice; }
    public void setCapturedPrice(BigDecimal capturedPrice) { this.capturedPrice = capturedPrice; }
    public BigDecimal getCurrentQuantity() { return currentQuantity; }
    public void setCurrentQuantity(BigDecimal currentQuantity) { this.currentQuantity = currentQuantity; }
    public BigDecimal getTargetQuantity() { return targetQuantity; }
    public void setTargetQuantity(BigDecimal targetQuantity) { this.targetQuantity = targetQuantity; }
}
