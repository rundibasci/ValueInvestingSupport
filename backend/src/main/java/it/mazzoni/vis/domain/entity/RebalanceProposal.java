package it.mazzoni.vis.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rebalance_proposal")
public class RebalanceProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String holdingsFingerprint;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime appliedAt;

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RebalanceLine> lines = new ArrayList<>();

    @PrePersist
    void created() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHoldingsFingerprint() { return holdingsFingerprint; }
    public void setHoldingsFingerprint(String holdingsFingerprint) { this.holdingsFingerprint = holdingsFingerprint; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
    public List<RebalanceLine> getLines() { return lines; }
}
