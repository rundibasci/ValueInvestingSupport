package it.mazzoni.vis.professional.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CompetencePreferencesResponse(List<String> preferredSectors, List<String> competenceIndustries,
                                            LocalDateTime updatedAt) {
}
