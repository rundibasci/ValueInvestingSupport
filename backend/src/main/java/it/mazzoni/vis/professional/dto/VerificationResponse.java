package it.mazzoni.vis.professional.dto;

import java.util.List;

public record VerificationResponse(String symbol, List<Flag> flags) {
    public record Flag(String field, String severity, String message) {
    }
}
