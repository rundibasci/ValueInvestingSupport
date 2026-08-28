package it.mazzoni.vis.thesis.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Mirrors it.mazzoni.vis.admin.SeedRunStatusResponse's shape/naming convention (DL5). */
public record ThesisRunStatusResponse(UUID thesisRunId, String status, String classification,
                                      BigDecimal confidence, Boolean humanReviewRequired,
                                      String errorCode, LocalDateTime generatedAt) {}
