# Plan - Phase I2: Observability

1. Baseline Observability Inventory
   - Review current Actuator, Micrometer, logging, WebClient, cache, scheduler/job, and frontend/demo configuration.
   - Map existing signals against the I2 roadmap requirements and the cloud-ready/full-tracing decisions.
   - Identify current log redaction, actuator exposure, metric naming, and correlation-id behavior.
   - Record any existing gaps that would prevent stakeholder evidence from being captured repeatably.

2. Observability Configuration Foundation
   - Add or refine profile-aware Actuator exposure for local, demo, and production-shaped environments.
   - Configure Prometheus-compatible metric export and stable management endpoint settings.
   - Configure JSON logging for application logs with trace id and span id fields.
   - Add environment-variable toggles for tracing/export behavior that are safe for Cloud Run later.
   - Document internal-only exposure expectations for actuator diagnostics.

3. Metrics Instrumentation
   - Instrument market-data client calls with provider, category, outcome, fallback, error class, and latency.
   - Instrument FMP quota usage or quota-risk counters where provider response/configuration allows.
   - Instrument Redis/cache behavior for hits, misses, evictions, and key cache groups without exposing raw cache keys.
   - Instrument screener, security review, valuation, scoring, portfolio, watchlist, and job flows with bounded latency and outcome metrics.
   - Review all metric labels for cardinality risk and remove unsafe labels.

4. Structured Logging And Redaction
   - Add request logging with method, path template where available, status, duration, trace id, and safe authenticated-role context.
   - Add provider/source logging for FMP, Yahoo fallback, cache decisions, quota/degraded states, and market-data failures.
   - Add job lifecycle logs for start, completion, skipped/degraded states, duration, processed counts, and error class.
   - Add redaction for authorization headers, cookies, API keys, JWTs, refresh tokens, OAuth values, and sensitive query/body fields.
   - Verify logs remain useful without dumping provider payloads or private user/security data.

5. Tracing And Correlation
   - Enable trace propagation for inbound HTTP requests and outbound WebClient calls.
   - Add spans or observations around cache operations, valuation/scoring services, screener queries, and job execution where automatic instrumentation is not enough.
   - Ensure trace id and span id are present in structured logs.
   - Provide a local trace sink, log correlation workflow, or documented fallback that lets a stakeholder-demo request be followed end to end.
   - Confirm tracing configuration can be disabled or redirected by environment for local and cloud-shaped profiles.

6. Health And Degraded-State Visibility
   - Review database, Redis, disk, application, market-data, and ingestion/job health indicators.
   - Add or refine health details that distinguish available, degraded, provider-limited, fallback-active, and unavailable states where safe.
   - Keep health responses suitable for internal/stakeholder diagnostics without leaking secrets or raw provider details.
   - Connect health/degraded-state evidence to existing cache-first and Yahoo-fallback behavior.

7. Stakeholder Demo Evidence Pack
   - Create or update a runbook that exercises a successful request, cache miss then cache hit, screener latency, provider fallback or simulated degraded provider, and one job/admin-triggered flow.
   - Capture example actuator metrics, structured log lines, trace/correlation ids, and health output for each scenario.
   - Explain evidence in operational terms: what happened, how to see it, and what degraded state means.
   - Preserve decision-support language and avoid presenting any demo portfolio or ticker output as investment advice.

8. Tests And Verification
   - Add focused tests for metric registration or observation behavior where practical.
   - Add tests for log redaction and safe correlation fields if the logging layer is testable.
   - Add tests or smoke checks for actuator endpoint configuration in local/test profiles.
   - Run backend tests and any frontend build/tests affected by demo documentation or UI hooks.
   - Record command results and stakeholder evidence links in `validation.md`.

9. Merge Readiness Review
   - Confirm I2 roadmap requirements are implemented, deferred with rationale, or assigned to K-phase operational work.
   - Confirm metric labels are bounded, logs are redacted, and traces do not include secrets.
   - Confirm the stakeholder evidence pack is reproducible without cloud infrastructure.
   - Confirm unrelated untracked logs and user changes are not included in the phase.
