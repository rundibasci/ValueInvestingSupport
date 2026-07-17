package it.mazzoni.vis.admin.dto;

import java.util.UUID;

public record SecurityIsinResponse(UUID securityId, String symbol, String isin) {}
