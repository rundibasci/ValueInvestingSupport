package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "stability_result")
public class StabilityResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(nullable = false)
    private LocalDate resultDate;

    @Column(nullable = false, length = 50)
    private String criterionCode;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(precision = 20, scale = 4)
    private BigDecimal actualValue;

    @Column(length = 255)
    private String message;

    public UUID getId() { return id; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public LocalDate getResultDate() { return resultDate; }
    public void setResultDate(LocalDate resultDate) { this.resultDate = resultDate; }
    public String getCriterionCode() { return criterionCode; }
    public void setCriterionCode(String criterionCode) { this.criterionCode = criterionCode; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getActualValue() { return actualValue; }
    public void setActualValue(BigDecimal actualValue) { this.actualValue = actualValue; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
