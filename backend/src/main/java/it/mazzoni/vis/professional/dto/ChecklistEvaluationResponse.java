package it.mazzoni.vis.professional.dto;

import it.mazzoni.vis.domain.entity.ChecklistEvaluation;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ChecklistEvaluationResponse(UUID id, UUID checklistId, String symbol, LocalDateTime evaluatedAt,
                                          List<Item> items) {
    public record Item(String label, String status, BigDecimal actualValue, String message) {
    }

    public static ChecklistEvaluationResponse from(ChecklistEvaluation evaluation) {
        return new ChecklistEvaluationResponse(
                evaluation.getId(),
                evaluation.getChecklist().getId(),
                evaluation.getSymbol(),
                evaluation.getEvaluatedAt(),
                evaluation.getItems().stream()
                        .map(item -> new Item(item.getLabel(), item.getStatus(), item.getActualValue(), item.getMessage()))
                        .toList()
        );
    }
}
