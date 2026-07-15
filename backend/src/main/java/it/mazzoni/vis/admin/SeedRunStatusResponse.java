package it.mazzoni.vis.admin;

import java.time.LocalDateTime;
import java.util.UUID;

public record SeedRunStatusResponse(UUID seedRunId, String scope, String status, int total, int processed,
                                    int succeeded, int partiallySeeded, int failed, String currentSymbol,
                                    String terminalReason, LocalDateTime createdAt, LocalDateTime startedAt,
                                    LocalDateTime updatedAt, LocalDateTime completedAt, int pollingIntervalMs) {}
