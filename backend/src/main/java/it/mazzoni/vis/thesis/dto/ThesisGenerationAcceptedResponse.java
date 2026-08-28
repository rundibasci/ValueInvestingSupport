package it.mazzoni.vis.thesis.dto;

import java.util.UUID;

/** Mirrors it.mazzoni.vis.admin.SeedRunAcceptedResponse's shape/naming convention (DL5). */
public record ThesisGenerationAcceptedResponse(UUID thesisRunId, String status, int pollingIntervalMs,
                                               String statusUrl) {}
