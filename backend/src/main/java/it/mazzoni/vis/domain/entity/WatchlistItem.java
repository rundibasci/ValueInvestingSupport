package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "watchlist_item")
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watchlist_id", nullable = false)
    private Watchlist watchlist;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(precision = 5, scale = 2)
    private BigDecimal mosAlertMin;

    @Column(precision = 5, scale = 2)
    private BigDecimal mosAlertMax;

    @Column(precision = 10, scale = 4)
    private BigDecimal fundamentalDegradeThreshold;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private MonitoringReason monitoringReason;

    @Column(length = 500)
    private String rationaleNote;

    @Column(nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Watchlist getWatchlist() { return watchlist; }
    public void setWatchlist(Watchlist watchlist) { this.watchlist = watchlist; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public BigDecimal getMosAlertMin() { return mosAlertMin; }
    public void setMosAlertMin(BigDecimal mosAlertMin) { this.mosAlertMin = mosAlertMin; }

    public BigDecimal getMosAlertMax() { return mosAlertMax; }
    public void setMosAlertMax(BigDecimal mosAlertMax) { this.mosAlertMax = mosAlertMax; }

    public BigDecimal getFundamentalDegradeThreshold() { return fundamentalDegradeThreshold; }
    public void setFundamentalDegradeThreshold(BigDecimal fundamentalDegradeThreshold) {
        this.fundamentalDegradeThreshold = fundamentalDegradeThreshold;
    }

    public MonitoringReason getMonitoringReason() { return monitoringReason; }
    public void setMonitoringReason(MonitoringReason monitoringReason) {
        this.monitoringReason = monitoringReason;
    }

    public String getRationaleNote() { return rationaleNote; }
    public void setRationaleNote(String rationaleNote) { this.rationaleNote = rationaleNote; }

    public LocalDateTime getAddedAt() { return addedAt; }
}
