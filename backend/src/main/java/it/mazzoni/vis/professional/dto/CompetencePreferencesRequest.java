package it.mazzoni.vis.professional.dto;

import java.util.List;

public record CompetencePreferencesRequest(List<String> preferredSectors, List<String> competenceIndustries) {
}
