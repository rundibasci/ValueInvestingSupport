package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "portfolio_import")
public class PortfolioImport {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;
    @Column(nullable = false) private String originalFilename;
    @Column(nullable = false, length = 64) private String checksum;
    @Column(nullable = false, length = 10) private String mode;
    @Column(nullable = false, length = 3) private String baseCurrency;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private int sourceRowCount;
    @Column(nullable = false) private int readyRowCount;
    @Column(nullable = false) private int warningCount;
    @Column(nullable = false) private int errorCount;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime expiresAt;
    private LocalDateTime committedAt;
    @OneToMany(mappedBy = "portfolioImport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rowNumber ASC")
    private List<PortfolioImportRow> rows = new ArrayList<>();
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String v) { originalFilename = v; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String v) { checksum = v; }
    public String getMode() { return mode; }
    public void setMode(String v) { mode = v; }
    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String v) { baseCurrency = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public int getSourceRowCount() { return sourceRowCount; }
    public void setSourceRowCount(int v) { sourceRowCount = v; }
    public int getReadyRowCount() { return readyRowCount; }
    public void setReadyRowCount(int v) { readyRowCount = v; }
    public int getWarningCount() { return warningCount; }
    public void setWarningCount(int v) { warningCount = v; }
    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int v) { errorCount = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime v) { expiresAt = v; }
    public LocalDateTime getCommittedAt() { return committedAt; }
    public void setCommittedAt(LocalDateTime v) { committedAt = v; }
    public List<PortfolioImportRow> getRows() { return rows; }
    public void addRow(PortfolioImportRow row) { rows.add(row); row.setPortfolioImport(this); }
}
