package it.mazzoni.vis.portfolio.dto;

public record PortfolioPreconditionDiagnostic(
        String code,
        String message,
        boolean blocksSimulation,
        boolean blocksRebalance,
        String recoveryAction
) {}
