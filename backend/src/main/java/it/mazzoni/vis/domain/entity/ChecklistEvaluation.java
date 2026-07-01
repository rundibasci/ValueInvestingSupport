package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "checklist_evaluation")
public class ChecklistEvaluation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checklist_id", nullable = false)
    private InvestmentChecklist checklist;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_id")
    private Security security;
    @Column(nullable = false, length = 20)
    private String symbol;
    @Column(nullable = false, updatable = false)
    private LocalDateTime evaluatedAt;
    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChecklistEvaluationItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (evaluatedAt == null) evaluatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public InvestmentChecklist getChecklist() { return checklist; }
    public void setChecklist(InvestmentChecklist checklist) { this.checklist = checklist; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public List<ChecklistEvaluationItem> getItems() { return items; }
}
