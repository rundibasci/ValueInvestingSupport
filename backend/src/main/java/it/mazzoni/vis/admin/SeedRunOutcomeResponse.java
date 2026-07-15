package it.mazzoni.vis.admin;

import java.time.LocalDateTime;

public record SeedRunOutcomeResponse(int position, String symbol, String status, String source,
                                     String reasonCode, String reason, String fallbackReason,
                                     String error, LocalDateTime completedAt) {}
