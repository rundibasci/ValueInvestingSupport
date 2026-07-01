package it.mazzoni.vis.professional.dto;

import java.time.LocalDateTime;

public record AdvisorAcknowledgementResponse(boolean acknowledged, LocalDateTime acknowledgedAt, String sessionKey,
                                             String disclaimer) {
}
