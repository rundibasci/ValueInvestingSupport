package it.mazzoni.vis.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record SetSecurityIsinRequest(@NotBlank String isin) {}
