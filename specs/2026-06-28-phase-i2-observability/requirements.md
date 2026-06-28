# Requirements - Phase I2: Observability

## Scope

Phase I2 adds production-shaped observability to the platform while preserving the current decision-support domain behavior. The phase should make local and stakeholder-demo runs explain what the system is doing, and prepare the same signals for later GCP/Cloud Run distribution.

The scope is cloud-ready observability with full tracing expectations:

- Prometheus/Micrometer metrics for FMP API calls, market-data fallback, cache hit rate, screener latency, valuation latency, ingestion/job execution, auth outcomes, and quota usage where provider data is available.
- Structured JSON logging with request correlation, user/role context where safe, provider/source context, job context, and no secrets or sensitive token values.
- Distributed tracing and span correlation across HTTP requests, market-data client calls, cache operations, valuation/scoring services, screener queries, and scheduled/admin-triggered jobs.
- Actuator health, metrics, and tracing-related endpoints configured for internal/stakeholder use locally and for safe exposure behind cloud controls later.
- Stakeholder-demo evidence that shows degraded states, fallback behavior, cache behavior, and trace/log correlation without requiring access to production infrastructure.

The phase must keep all investment-facing copy within the decision-support boundary. Observability should explain platform behavior and data availability; it must not turn diagnostics into buy/sell instructions or personalized financial advice.

## Context

- `specs/mission.md` requires data before opinion, transparency, conservative defaults, cache-first provider behavior, Yahoo fallback, explainable missing data, visible portfolio exposure, and mandatory MiFID II decision-support boundaries.
- `specs/tech-stack.md` defines Spring Boot 3, Java 21, Micrometer, Actuator, structured JSON logging, Prometheus/Grafana direction, Redis, PostgreSQL, FMP/Yahoo market-data behavior, and GCP/Cloud Run as the distribution path.
- `specs/roadmap.md` defines Phase I2 as Prometheus metrics via Micrometer, structured JSON logging, FMP API call count/latency, cache hit rate, screener latency, FMP quota monitoring, and internally exposed Actuator endpoints.
- The user selected cloud readiness, full tracing, and stakeholder-demo evidence as the feature-spec priorities for this phase.
- Phase I1 should provide deterministic tests and replay evidence that I2 can use for repeatable smoke flows.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| I2 scope | Cloud readiness | The implementation should prepare local/stakeholder observability for GCP distribution without prematurely implementing K-phase infrastructure. |
| Primary signals | Metrics, structured logs, and full tracing | Metrics cover operational health, logs explain incidents, and traces connect request-to-provider-to-cache-to-domain behavior. |
| Validation emphasis | Stakeholder-demo evidence | The phase should produce visible, repeatable evidence that non-technical stakeholders can understand, supported by technical checks. |
| Cloud boundary | Configuration-ready, not deployed | Add Cloud Run-friendly log/metric/trace conventions and documentation, but do not require Terraform, Cloud SQL, Memorystore, or Cloud Monitoring deployment in I2. |
| Sensitive data | Redacted by default | No API keys, JWTs, refresh tokens, OAuth codes, authorization headers, personal secrets, or provider payload dumps may appear in logs, traces, metrics labels, screenshots, or demo evidence. |
| Actuator exposure | Internal by default | Local and stakeholder environments may expose diagnostics intentionally; production-shaped configuration must assume network/IAM restrictions before public access. |

## Functional Requirements

1. Metrics must capture external market-data calls by provider, endpoint/category, status, fallback usage, error class, and latency without using high-cardinality labels such as raw URLs, user IDs, request IDs, or arbitrary symbols where avoidable.
2. Metrics must expose cache behavior for market-data and computed-domain caches, including hit/miss/eviction counts and practical cache latency where the existing cache layer supports it.
3. Metrics must expose screener, security review, valuation, scoring, portfolio, watchlist, and job latency at useful service/controller boundaries, focusing first on workflows already exercised by the demo and I1 replay paths.
4. Metrics must expose FMP quota or quota-risk monitoring where the provider response or configuration makes this possible; if unavailable, the implementation must document the limitation and still count calls and fallback outcomes.
5. Structured JSON logs must include timestamp, level, logger, message, trace/correlation id, span id where available, request method/path/status, duration, authenticated role where safe, provider/source, job name, and error class for failures.
6. Logs must redact credentials and sensitive values from headers, query parameters, request bodies, exception messages, and provider/client configuration.
7. Tracing must create or propagate spans for inbound HTTP requests, outbound FMP/Yahoo calls, Redis/cache operations where supported, valuation/scoring calculations, screener queries, and job execution.
8. Trace context must be correlated with structured logs and documented so a stakeholder-demo request can be followed from UI/API action through logs, metrics, and spans.
9. Actuator endpoints must expose health and metrics needed for local/stakeholder validation while maintaining a clear internal-only posture for production-shaped environments.
10. Health indicators must distinguish application, database, Redis/cache, market-data provider reachability or degraded fallback state, and ingestion/job health where existing components support those checks.
11. Observability configuration must work with local profiles and be compatible with containerized/Cloud Run execution through environment variables, without committing secrets.
12. The implementation must add or update demo/runbook documentation showing how to generate stakeholder evidence for a successful request, a fallback/degraded-provider path, cache hit/miss behavior, screener latency, and at least one traced job or admin-triggered flow.

## Non-Goals

- Creating Terraform, Cloud Monitoring dashboards, Grafana dashboards, alert policies, or GCP resources.
- Replacing existing business behavior for market-data clients, valuation, scoring, screener, portfolio, or watchlist features.
- Logging raw provider payloads, raw financial statement bodies, JWTs, refresh tokens, OAuth values, API keys, or personally sensitive claims.
- Requiring a real FMP key, live Yahoo availability, or deployed cloud infrastructure for required merge validation.
- Adding personalized investment advice, order recommendations, or advice-like interpretations to operational diagnostics.
- Completing Phase K cloud deployment or Phase I1 test coverage gaps.

## Dependencies And Constraints

- Existing untracked log files must remain untouched unless the user explicitly asks to clean them.
- Secrets must never be committed. `.env`, FMP keys, JWT private keys, Google OAuth credentials, and provider tokens remain outside source control.
- Prefer existing Spring Boot Actuator, Micrometer, WebClient, Redis, logging, and test conventions already present in the repository.
- Keep metric tags bounded and stable to avoid high-cardinality production risk.
- Stakeholder-demo evidence should be reproducible locally with seeded or mocked data where possible.
- Any optional live-provider validation must be clearly marked optional and must not block merge.
