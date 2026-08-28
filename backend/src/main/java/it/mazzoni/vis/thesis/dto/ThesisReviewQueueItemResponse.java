package it.mazzoni.vis.thesis.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** ADMIN-only review queue row — TRAIN-12.5's audit-retention scope (HUMAN_REVIEW_PENDING
 * status or non-empty dataWarnings), mirroring the existing alert-queue (G2) response shape. */
public record ThesisReviewQueueItemResponse(UUID id, String symbol, String companyName, String status,
                                            String classification, Boolean humanReviewRequired,
                                            boolean dataWarningsPresent, LocalDateTime generatedAt) {}
