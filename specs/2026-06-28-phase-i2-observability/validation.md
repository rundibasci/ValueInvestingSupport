# Validation - Phase I2: Observability

## Merge Criteria

Phase I2 is mergeable when the platform exposes cloud-ready metrics, logs, tracing, health, and stakeholder-demo evidence without requiring deployed cloud infrastructure or live provider credentials.

1. Metrics are available and bounded.
   - Prometheus-compatible actuator metrics include market-data call counts/latency, fallback outcomes, cache hit/miss behavior, screener latency, valuation/scoring latency, job execution, and FMP quota or quota-risk evidence where available.
   - Metric labels avoid high-cardinality values such as arbitrary symbols, raw URLs, user IDs, request IDs, or free-form exception messages.

2. Structured JSON logs are safe and correlated.
   - Application logs include trace id/span id, request method/path/status, duration, provider/source, job context, and error class where relevant.
   - Logs redact authorization headers, cookies, JWTs, refresh tokens, API keys, OAuth values, secrets, and sensitive request/body fields.
   - Logs do not dump raw provider payloads or personally sensitive claims.

3. Tracing covers the important request paths.
   - Inbound API requests and outbound FMP/Yahoo calls are traced.
   - Cache, valuation/scoring, screener, and job boundaries are traced or observed where supported by the implementation.
   - A request can be followed across trace context and structured logs during the stakeholder demo flow.

4. Health and actuator exposure are production-shaped.
   - Health distinguishes app, DB, Redis/cache, market-data/degraded provider state, and job/ingestion state where those components exist.
   - Actuator settings are usable locally and documented as internal-only for stakeholder/cloud-shaped environments.
   - No actuator output exposes secrets or sensitive runtime configuration.

5. Stakeholder demo evidence is repeatable.
   - A documented local/stakeholder run demonstrates a successful request, cache miss then hit, screener latency, fallback or simulated degraded provider behavior, and one traced job/admin flow.
   - Evidence includes representative metric names, redacted structured log examples, trace/correlation ids, and health output.
   - Evidence explains operational behavior without framing stock outputs as personalized advice.

6. Required validation does not depend on live external services.
   - Merge validation can run with local, mocked, simulated, or seeded data.
   - Any live FMP/Yahoo check is optional, clearly labeled, and excluded from required merge criteria.

7. Repository hygiene is clean.
   - No secrets are committed.
   - Existing unrelated log files remain untracked or ignored.
   - Documentation and evidence files avoid sensitive screenshots, tokens, or environment values.

## Evidence To Record During Implementation

| Check | Command or Evidence | Result |
|---|---|---|
| Backend tests | `cd backend; mvn -q test` | Passed on 2026-06-28 |
| Focused observability tests | `cd backend; mvn -q "-Dtest=RedactionTest,ObservedCacheTest,RequestCorrelationFilterTest,MarketDataHealthIndicatorTest,JobRunLoggerTest,FmpWithYahooFallbackMarketDataClientTest" test` | Passed on 2026-06-28 |
| Cache metrics regression | `cd backend; mvn -q "-Dtest=CacheEvictionServiceTest,ObservedCacheTest,MarketDataClientCacheTest" test` | Passed on 2026-06-28 |
| Prometheus metrics smoke | `curl http://localhost:8080/actuator/prometheus` after local run | Pending |
| Health smoke | `curl http://localhost:8080/actuator/health` and any relevant health groups | Pending |
| Structured log sample | Capture redacted JSON log lines with trace/span correlation | Pending |
| Trace correlation sample | Capture a trace/correlation id linking API request, provider/cache/service work, and logs | Pending |
| Cache/fallback evidence | Runbook output showing cache miss/hit and fallback or simulated degraded provider path | Pending |
| Screener latency evidence | Runbook output showing screener metric and log/trace correlation | Pending |
| Job/admin flow evidence | Runbook output showing one traced job/admin-triggered flow | Pending |
| Frontend/demo validation | Run stakeholder-visible demo steps affected by observability documentation | Pending |

## Implementation Evidence

- Added Prometheus registry, Micrometer tracing bridge, AOP instrumentation, and JSON log encoding dependencies.
- Added request correlation with `X-Correlation-ID` response propagation.
- Added bounded metrics for market-data provider calls, fallback events, cache access, screener/review/valuation/scoring/portfolio/watchlist services, and job execution.
- Added `marketData` health details for configured source, last provider, last success, and degraded fallback status.
- Added JSON console logs for local, localstack, docker, and prod-shaped profiles.
- Added stakeholder runbook: `stakeholder-observability-runbook.md`.

## Stakeholder Demo Scenarios

1. Successful research request
   - Trigger a representative authenticated research flow, such as quick analysis, security review, or screener depending on available local data.
   - Show the request status, response timing, trace/correlation id, relevant service metrics, and redacted structured logs.

2. Cache miss then cache hit
   - Trigger the same market-data-backed or computed-domain flow twice.
   - Show cache miss evidence on the first run and hit evidence on the second run through metrics/logs.

3. Provider degraded or fallback path
   - Simulate missing FMP key, provider-limited response, or controlled client failure where the existing architecture supports it.
   - Show degraded/fallback metrics, logs, health state, and decision-support-safe user/API behavior.

4. Screener latency
   - Run a seeded screener query.
   - Show bounded latency metrics, trace/log correlation, and no high-cardinality metric labels.

5. Job or admin-triggered flow
   - Trigger a safe local job/admin action such as a seed, pipeline, cache eviction, or job run endpoint based on available implementation.
   - Show job lifecycle logs, outcome metrics, and trace/correlation evidence.

## Known Validation Boundaries

- Cloud Monitoring, Grafana dashboards, alert policies, Terraform, and deployed GCP resources are Phase K responsibilities unless explicitly pulled forward later.
- Full provider quota accuracy may depend on what FMP exposes in responses or account metadata; I2 must still count calls and degraded/fallback outcomes if exact quota remaining is unavailable.
- Live FMP/Yahoo smoke tests are useful but optional because required validation must remain repeatable without external services.
