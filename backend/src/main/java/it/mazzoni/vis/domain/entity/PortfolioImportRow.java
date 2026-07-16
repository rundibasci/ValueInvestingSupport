package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "portfolio_import_row", uniqueConstraints = @UniqueConstraint(columnNames = {"import_id", "row_number"}))
public class PortfolioImportRow {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "import_id", nullable = false)
    private PortfolioImport portfolioImport;
    @Column(nullable = false) private int rowNumber;
    @Column(nullable = false, length = 500) private String productName;
    @Column(length = 50) private String sourceCode;
    @Column(length = 12) private String isin;
    @Column(precision = 20, scale = 6) private BigDecimal quantity;
    @Column(precision = 20, scale = 6) private BigDecimal sourceLastPrice;
    @Column(length = 3) private String nativeCurrency;
    @Column(precision = 20, scale = 4) private BigDecimal nativeValue;
    @Column(precision = 20, scale = 4) private BigDecimal baseValue;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "resolved_security_id") private Security resolvedSecurity;
    @Column(nullable = false, length = 20) private String classification;
    @Column(nullable = false, length = 30) private String status;
    @Column(length = 1000) private String warningText;
    @Column(length = 1000) private String errorText;
    @Column(length = 30) private String committedOutcome;
    public UUID getId() { return id; }
    public PortfolioImport getPortfolioImport() { return portfolioImport; }
    public void setPortfolioImport(PortfolioImport v) { portfolioImport = v; }
    public int getRowNumber() { return rowNumber; }
    public void setRowNumber(int v) { rowNumber = v; }
    public String getProductName() { return productName; }
    public void setProductName(String v) { productName = v; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String v) { sourceCode = v; }
    public String getIsin() { return isin; }
    public void setIsin(String v) { isin = v; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal v) { quantity = v; }
    public BigDecimal getSourceLastPrice() { return sourceLastPrice; }
    public void setSourceLastPrice(BigDecimal v) { sourceLastPrice = v; }
    public String getNativeCurrency() { return nativeCurrency; }
    public void setNativeCurrency(String v) { nativeCurrency = v; }
    public BigDecimal getNativeValue() { return nativeValue; }
    public void setNativeValue(BigDecimal v) { nativeValue = v; }
    public BigDecimal getBaseValue() { return baseValue; }
    public void setBaseValue(BigDecimal v) { baseValue = v; }
    public Security getResolvedSecurity() { return resolvedSecurity; }
    public void setResolvedSecurity(Security v) { resolvedSecurity = v; }
    public String getClassification() { return classification; }
    public void setClassification(String v) { classification = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getWarningText() { return warningText; }
    public void setWarningText(String v) { warningText = v; }
    public String getErrorText() { return errorText; }
    public void setErrorText(String v) { errorText = v; }
    public String getCommittedOutcome() { return committedOutcome; }
    public void setCommittedOutcome(String v) { committedOutcome = v; }
}
