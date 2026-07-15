package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.portfolio.dto.PortfolioPreconditionsResponse;

public class PortfolioPreconditionException extends RuntimeException {
    private final PortfolioPreconditionsResponse diagnostics;

    public PortfolioPreconditionException(String message, PortfolioPreconditionsResponse diagnostics) {
        super(message);
        this.diagnostics = diagnostics;
    }

    public PortfolioPreconditionsResponse getDiagnostics() {
        return diagnostics;
    }
}
