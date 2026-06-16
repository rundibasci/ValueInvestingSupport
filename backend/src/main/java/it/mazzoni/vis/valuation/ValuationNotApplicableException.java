package it.mazzoni.vis.valuation;

public class ValuationNotApplicableException extends RuntimeException {
    public ValuationNotApplicableException(String symbol) {
        super("No valuation model applicable for: " + symbol);
    }
}
