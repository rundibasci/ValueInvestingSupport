# Stakeholder Observability Evidence Runbook - Phase I2

This runbook captures repeatable local evidence for Phase I2 without requiring deployed cloud infrastructure or live market-data credentials. Use the `localstack` profile when possible because it runs with H2, Docker Redis, seeded local data, JWT auth, JSON logs, Prometheus metrics, and actuator diagnostics.

## Start Local Stakeholder Stack

1. Start Redis.
   - `docker compose -f docker-compose.demo.yml up -d`

2. Start the backend.
   - `cd backend`
   - `mvn spring-boot:run "-Dspring-boot.run.profiles=localstack"`

3. Confirm health and diagnostics exposure.
   - `curl http://localhost:8080/actuator/health`
   - `curl http://localhost:8080/actuator/prometheus`

Expected evidence:

- Health includes application dependencies such as DB, Redis, `marketData`, and `ingestionJobs` when jobs are enabled.
- Prometheus output includes `application="vis-backend"` tags.
- Console logs are JSON in local/stakeholder profiles and include MDC fields such as `correlation.id`, `traceId`, `spanId`, `job.name`, and `job.run.id` when those contexts exist.

## Scenario 1 - Successful Authenticated Research Request

1. Log in with the localstack admin user.
   - `curl -s -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin\"}"`

2. Use the access token to call a representative research endpoint.
   - `curl -i http://localhost:8080/api/v1/screener -H "Authorization: Bearer <ACCESS_TOKEN>" -H "Content-Type: application/json" -H "X-Correlation-ID: demo-research-001" -d "{}"`

3. Inspect metrics and logs.
   - `curl -s http://localhost:8080/actuator/prometheus | findstr vis_screener_latency`

Expected evidence:

- The HTTP response echoes `X-Correlation-ID: demo-research-001`.
- Logs include `http_request`, method/path/status/duration, role context where available, and the same correlation id.
- Metrics include `vis_screener_latency_seconds`.

## Scenario 2 - Cache Miss Then Cache Hit

1. Trigger a cache-backed flow twice. A seed or quick-analysis flow is ideal when local data is available.
   - `curl -i http://localhost:8080/api/v1/securities/AAPL/quick-analysis -H "Authorization: Bearer <ACCESS_TOKEN>" -H "X-Correlation-ID: demo-cache-001"`
   - Repeat the same command with `X-Correlation-ID: demo-cache-002`.

2. Inspect cache metrics.
   - `curl -s http://localhost:8080/actuator/prometheus | findstr vis_cache_access`

Expected evidence:

- `vis_cache_access_total{cache="mdc-quote",outcome="miss"}` appears after the first cache-backed request when data is loaded.
- `vis_cache_access_total{cache="mdc-quote",outcome="hit"}` appears after repeated access to the same cache key.
- Cache labels use cache group names, not raw keys or user identifiers.

## Scenario 3 - Provider Degraded Or Fallback Path

Use one of these approaches:

- Start local profile with `MARKET_DATA_SOURCE=fmp` and no usable FMP key, then execute a provider-backed request that supports Yahoo fallback.
- In tests or a controlled local run, simulate `PLAN_RESTRICTION` from the FMP client.

Inspect:

- `curl http://localhost:8080/actuator/health`
- `curl -s http://localhost:8080/actuator/prometheus | findstr vis_marketdata_fallback`

Expected evidence:

- `marketData` health reports configured source, last provider, last success, last fallback time, and fallback reason.
- Fallback metrics include provider, operation, fallback provider, and error reason.
- Logs contain `market_data_fallback` with provider/source context and no API key or provider payload.

## Scenario 4 - Screener Latency

1. Run a seeded screener query.
   - `curl -i -X POST http://localhost:8080/api/v1/screener -H "Authorization: Bearer <ACCESS_TOKEN>" -H "Content-Type: application/json" -H "X-Correlation-ID: demo-screener-001" -d "{\"page\":0,\"pageSize\":10}"`

2. Inspect:
   - `curl -s http://localhost:8080/actuator/prometheus | findstr vis_screener_latency`

Expected evidence:

- The screener timer records success/error outcome and bounded operation/component tags.
- No metric includes raw symbols, user ids, request ids, or arbitrary filter payloads.

## Scenario 5 - Job Or Admin Flow

1. Trigger a safe admin operation such as cache eviction or a job run supported by the current local stack.
   - `curl -i -X DELETE http://localhost:8080/api/v1/admin/cache/AAPL -H "Authorization: Bearer <ACCESS_TOKEN>" -H "X-Correlation-ID: demo-admin-001"`
   - Or trigger an existing job endpoint from the full demo page.

2. Inspect:
   - `curl -s http://localhost:8080/actuator/prometheus | findstr vis_job`
   - Review JSON logs for `job_started`, `job_completed`, or `job_failed` when a job is triggered.

Expected evidence:

- Job logs include job name and job run id in MDC fields.
- Job metrics include `vis_job_execution_total` and `vis_job_duration_seconds`.
- Admin/cache flows can be correlated through `X-Correlation-ID`.

## Evidence Boundaries

- Do not paste JWTs, refresh tokens, API keys, authorization headers, cookies, or `.env` values into evidence files.
- Do not capture raw provider payloads.
- Any ticker output remains decision-support context only; the evidence is about system operation, not investment advice.
- Live FMP/Yahoo checks are optional and should be labeled as live-provider smoke evidence, not required merge evidence.
