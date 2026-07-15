package it.mazzoni.vis.admin.dto;

import jakarta.validation.constraints.NotNull;

public record SetUserActiveRequest(@NotNull Boolean active) {}
