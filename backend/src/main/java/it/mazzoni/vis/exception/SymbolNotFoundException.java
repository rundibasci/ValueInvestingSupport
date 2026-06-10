package it.mazzoni.vis.exception;

public class SymbolNotFoundException extends RuntimeException {
    public SymbolNotFoundException(String symbol) {
        super("Symbol not found: " + symbol);
    }
}
