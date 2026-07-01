package it.mazzoni.vis.professional.dto;

import it.mazzoni.vis.domain.entity.ChecklistCriterion;
import it.mazzoni.vis.domain.entity.InvestmentChecklist;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ChecklistResponse(UUID id, String name, String description, List<Criterion> criteria,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
    public record Criterion(UUID id, String label, String criterionType, String metricKey,
                            String operator, BigDecimal threshold, int displayOrder) {
        static Criterion from(ChecklistCriterion criterion) {
            return new Criterion(criterion.getId(), criterion.getLabel(), criterion.getCriterionType(),
                    criterion.getMetricKey(), criterion.getOperator(), criterion.getThreshold(),
                    criterion.getDisplayOrder());
        }
    }

    public static ChecklistResponse from(InvestmentChecklist checklist) {
        return new ChecklistResponse(checklist.getId(), checklist.getName(), checklist.getDescription(),
                checklist.getCriteria().stream().map(Criterion::from).toList(),
                checklist.getCreatedAt(), checklist.getUpdatedAt());
    }
}
