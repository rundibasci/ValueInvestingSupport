package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "checklist_evaluation_item")
public class ChecklistEvaluationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false)
    private ChecklistEvaluation evaluation;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criterion_id")
    private ChecklistCriterion criterion;
    @Column(nullable = false, length = 240)
    private String label;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(precision = 20, scale = 4)
    private BigDecimal actualValue;
    @Column(columnDefinition = "TEXT")
    private String message;

    public UUID getId() { return id; }
    public ChecklistEvaluation getEvaluation() { return evaluation; }
    public void setEvaluation(ChecklistEvaluation evaluation) { this.evaluation = evaluation; }
    public ChecklistCriterion getCriterion() { return criterion; }
    public void setCriterion(ChecklistCriterion criterion) { this.criterion = criterion; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getActualValue() { return actualValue; }
    public void setActualValue(BigDecimal actualValue) { this.actualValue = actualValue; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
