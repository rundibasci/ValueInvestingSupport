package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "checklist_criterion")
public class ChecklistCriterion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checklist_id", nullable = false)
    private InvestmentChecklist checklist;
    @Column(nullable = false, length = 240)
    private String label;
    @Column(nullable = false, length = 30)
    private String criterionType;
    @Column(length = 80)
    private String metricKey;
    @Column(length = 10)
    private String operator;
    @Column(precision = 20, scale = 4)
    private BigDecimal threshold;
    @Column(nullable = false)
    private int displayOrder;

    public UUID getId() { return id; }
    public InvestmentChecklist getChecklist() { return checklist; }
    public void setChecklist(InvestmentChecklist checklist) { this.checklist = checklist; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getCriterionType() { return criterionType; }
    public void setCriterionType(String criterionType) { this.criterionType = criterionType; }
    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
