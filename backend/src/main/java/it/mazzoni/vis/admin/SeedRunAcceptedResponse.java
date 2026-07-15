package it.mazzoni.vis.admin;

import java.util.UUID;

public record SeedRunAcceptedResponse(UUID seedRunId, String status, int normalizedTickerCount,
                                      String progressUrl, String outcomesUrl, int pollingIntervalMs,
                                      boolean joinedExistingRun) {}
