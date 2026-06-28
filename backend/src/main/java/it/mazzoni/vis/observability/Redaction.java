package it.mazzoni.vis.observability;

import java.util.regex.Pattern;

public final class Redaction {

    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(authorization|cookie|set-cookie|apikey|api-key|api_key|token|jwt|secret|password|refreshToken|accessToken)=([^,\\s}]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");

    private Redaction() {}

    public static String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String redacted = BEARER.matcher(value).replaceAll("Bearer_[REDACTED]");
        return SENSITIVE_ASSIGNMENT.matcher(redacted).replaceAll("$1=[REDACTED]");
    }
}
