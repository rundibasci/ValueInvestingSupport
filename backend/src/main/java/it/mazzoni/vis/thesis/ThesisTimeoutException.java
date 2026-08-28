package it.mazzoni.vis.thesis;

/** Thrown by a {@link GeminiCaller} implementation to signal a timeout distinctly from any
 * other failure — maps to {@link ThesisErrorCode#TIMEOUT}, not
 * {@link ThesisErrorCode#SCHEMA_VALIDATION_FAILED}. */
public class ThesisTimeoutException extends RuntimeException {
    public ThesisTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
