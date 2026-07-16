package it.mazzoni.vis.portfolio.importing.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record IsinMappingRequest(@NotNull UUID rowId, @NotNull UUID securityId) { }
