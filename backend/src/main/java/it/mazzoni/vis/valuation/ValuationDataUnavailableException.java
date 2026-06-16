package it.mazzoni.vis.valuation;

public class ValuationDataUnavailableException extends RuntimeException {
    public ValuationDataUnavailableException(String symbol) {
        super("No fundamental data available for: " + symbol);
    }
}
