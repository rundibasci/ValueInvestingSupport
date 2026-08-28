package it.mazzoni.vis.thesis;

import java.time.LocalDateTime;

/** Never a generic 500 — GlobalExceptionHandler maps this to a structured 429 body
 * (RATE_LIMIT_EXCEEDED, limit, resetsAt). */
public class ThesisRateLimitExceededException extends RuntimeException {
    private final int limit;
    private final LocalDateTime resetsAt;

    public ThesisRateLimitExceededException(int limit, LocalDateTime resetsAt) {
        super("Daily thesis generation limit (" + limit + ") exceeded; resets at " + resetsAt);
        this.limit = limit;
        this.resetsAt = resetsAt;
    }

    public int limit() { return limit; }
    public LocalDateTime resetsAt() { return resetsAt; }
}
