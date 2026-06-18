package it.mazzoni.vis.valuation;

import java.time.LocalDate;

public class StaleDataException extends RuntimeException {
    public StaleDataException(String symbol, LocalDate dataAsOf) {
        super("Fundamental data for " + symbol + " is stale (as of " + dataAsOf + "). Refresh required.");
    }
}
