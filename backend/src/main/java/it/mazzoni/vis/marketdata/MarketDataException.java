package it.mazzoni.vis.marketdata;

public class MarketDataException extends RuntimeException {

    public enum ErrorCode { NOT_FOUND, SERVICE_UNAVAILABLE, INVALID_SYMBOL }

    private final ErrorCode errorCode;
    private final String symbol;

    public MarketDataException(ErrorCode errorCode, String symbol) {
        super(errorCode + " for symbol: " + symbol);
        this.errorCode = errorCode;
        this.symbol = symbol;
    }

    public MarketDataException(ErrorCode errorCode, String symbol, Throwable cause) {
        super(errorCode + " for symbol: " + symbol, cause);
        this.errorCode = errorCode;
        this.symbol = symbol;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public String getSymbol() { return symbol; }
}
