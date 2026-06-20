package it.mazzoni.vis.portfolio.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePortfolioRequest(
        @NotBlank String name,
        String description
) {}
