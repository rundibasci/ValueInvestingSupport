package it.mazzoni.vis.demo;

import it.mazzoni.vis.exception.MarketDataUnavailableException;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.valuation.StaleDataException;
import it.mazzoni.vis.valuation.ValuationDataUnavailableException;
import it.mazzoni.vis.valuation.ValuationNotApplicableException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SymbolNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleSymbolNotFound(SymbolNotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(MarketDataUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> handleMarketDataUnavailable(MarketDataUnavailableException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(StaleDataException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, String> handleStaleData(StaleDataException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(ValuationDataUnavailableException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, String> handleValuationDataUnavailable(ValuationDataUnavailableException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(ValuationNotApplicableException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, String> handleValuationNotApplicable(ValuationNotApplicableException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(MarketDataException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> handleMarketDataException(
            MarketDataException ex) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        if (ex.getErrorCode() == MarketDataException.ErrorCode.NOT_FOUND) {
            status = HttpStatus.NOT_FOUND;
        } else if (ex.getErrorCode() == MarketDataException.ErrorCode.INVALID_SYMBOL) {
            status = HttpStatus.BAD_REQUEST;
        } else if (ex.getErrorCode() == MarketDataException.ErrorCode.PLAN_RESTRICTION) {
            status = HttpStatus.PAYMENT_REQUIRED;
        }
        return org.springframework.http.ResponseEntity.status(status)
                .body(Map.of("error", ex.getMessage()));
    }
}
