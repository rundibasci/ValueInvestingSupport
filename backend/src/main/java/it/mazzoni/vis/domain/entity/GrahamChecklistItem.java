package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "graham_checklist_item")
public class GrahamChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "valuation_result_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ValuationResult valuationResult;

    @Column(nullable = false, length = 80)
    private String criterionCode;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(precision = 15, scale = 6)
    private BigDecimal actualValue;

    public UUID getId() { return id; }
    public ValuationResult getValuationResult() { return valuationResult; }
    public void setValuationResult(ValuationResult valuationResult) { this.valuationResult = valuationResult; }
    public String getCriterionCode() { return criterionCode; }
    public void setCriterionCode(String criterionCode) { this.criterionCode = criterionCode; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getActualValue() { return actualValue; }
    public void setActualValue(BigDecimal actualValue) { this.actualValue = actualValue; }
}
