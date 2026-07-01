package it.mazzoni.vis.professional.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ChecklistRequest(
        @NotBlank String name,
        String description,
        @Valid List<ChecklistCriterionRequest> criteria
) {
}
