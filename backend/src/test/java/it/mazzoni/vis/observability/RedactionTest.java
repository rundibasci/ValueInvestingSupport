package it.mazzoni.vis.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedactionTest {

    @Test
    void redact_masksSensitiveAssignmentsAndBearerTokens() {
        String input = "authorization=Bearer abc.def.ghi apiKey=secret password=hunter2 normal=value";

        String redacted = Redaction.redact(input);

        assertThat(redacted).contains("authorization=[REDACTED]");
        assertThat(redacted).contains("apiKey=[REDACTED]");
        assertThat(redacted).contains("password=[REDACTED]");
        assertThat(redacted).contains("normal=value");
        assertThat(redacted).doesNotContain("abc.def.ghi", "secret", "hunter2");
    }
}
