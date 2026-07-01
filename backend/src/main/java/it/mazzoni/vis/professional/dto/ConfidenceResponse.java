package it.mazzoni.vis.professional.dto;

import java.util.List;

public record ConfidenceResponse(String symbol, String overallLevel, List<Factor> factors) {
    public record Factor(String name, String level, String message) {
    }
}
