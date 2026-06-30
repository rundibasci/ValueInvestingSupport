package it.mazzoni.vis.auth.oauth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OAuthSecurityEventService {

    private static final Logger log = LoggerFactory.getLogger(OAuthSecurityEventService.class);

    private final MeterRegistry meterRegistry;

    public OAuthSecurityEventService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAccountCreated() {
        record("google_account_created", "success", "new_verified_email");
    }

    public void recordAccountLinked() {
        record("google_account_linked", "success", "existing_verified_email");
    }

    public void recordIdentityReused() {
        record("google_identity_reused", "success", "existing_provider_subject");
    }

    public void recordCallbackSuccess() {
        record("google_callback", "success", "verified_email");
    }

    public void recordCallbackRejected(String reason) {
        record("google_callback", "rejected", sanitizeReason(reason));
    }

    public void recordHandoffExchangeSuccess() {
        record("google_handoff_exchange", "success", "valid_code");
    }

    public void recordHandoffExchangeRejected() {
        record("google_handoff_exchange", "rejected", "invalid_or_expired_code");
    }

    public void recordResolutionRejected(String reason) {
        record("google_identity_resolution", "rejected", sanitizeReason(reason));
    }

    private void record(String event, String outcome, String reason) {
        log.info("oauth_security_event event={} provider=google outcome={} reason={}", event, outcome, reason);
        Counter.builder("vis.oauth.security.events")
                .tag("provider", "google")
                .tag("event", event)
                .tag("outcome", outcome)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    private String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unspecified";
        }
        return reason.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
    }
}
