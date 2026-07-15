# Market Data Fallback Detail and Observability — Feature Report

Date: 2026-07-15  
Environment observed: clean `realDemo`, configured source `fmp`

## Executive Summary

The platform currently invokes Yahoo Finance in at least one fallback or enrichment path, but it does not expose enough evidence to determine precisely which ticker, operation, response field, or persisted value came from Yahoo.

This is particularly important because the current FMP plan is expected to provide the data required by the platform. Yahoo fallback should therefore be exceptional. Any invocation may indicate an incorrect FMP endpoint, request parameters, response mapping, plan-restriction classification, or missing-field policy rather than a legitimate coverage limitation.

The requested feature is complete, queryable fallback provenance from provider request through persisted platform data and ADMIN presentation.

## Observed Behavior

During the 2026-07-15 real-demo seed:

- The configured market-data source was `fmp`.
- The health indicator reported `lastFallbackReason: PLAN_RESTRICTION`.
- The application called Yahoo Finance through the FMP-with-Yahoo wrapper.
- No `market_data_fallback ... fallbackProvider=yahoo` entry was present in the backend logs for the run.
- `ingestion_event` contained only `source=fmp`, with 62 successful, 128 skipped, and 3 failed events.
- The existing events did not identify Yahoo enrichment, the affected symbols, operations, or fields.
- The ADMIN walkthrough could not reconstruct which persisted values came from FMP and which came from Yahoo.

The current health state proves that the fallback path was entered, but it is insufficient as an audit trail.

## Current Observability Gap

There are two materially different Yahoo paths:

1. **Explicit fallback**: FMP returns `PLAN_RESTRICTION` for profile, fundamentals, ratios, or quote, and the operation is retried through Yahoo.
2. **Silent enrichment**: FMP returns a nominally successful but incomplete profile or quote, and Yahoo is queried for fields such as exchange or volume.

The explicit path produces a structured log through `recordFallback`. The enrichment path records Yahoo in a request-local `SourceTracker`, but does not create an ingestion event or an equivalent persistent audit record. The request-local summary is also lost after the seed response unless the caller captures it.

As a result, the platform cannot currently answer:

- Which ticker triggered Yahoo?
- Which FMP endpoint and operation caused the fallback?
- Did FMP return HTTP 402, another error, an empty payload, or a payload missing required fields?
- Was Yahoo merely called, or was its value actually accepted and persisted?
- Which exact fields came from Yahoo?
- Did Yahoo succeed, fail, or return another incomplete value?
- How frequently is fallback occurring under the current paid FMP plan?

## Required Feature

### 1. Persist one event for every fallback attempt

Create a durable event whenever a secondary provider is considered, including enrichment attempts. Record the event even when Yahoo fails or its value is rejected.

Minimum fields:

| Field | Description |
|---|---|
| Timestamp | Start and completion time of the attempt |
| Correlation ID | Link to HTTP request, seed operation, or job run |
| Job run ID | Startup seed or scheduled/manual job identifier |
| Symbol | Affected ticker |
| Data category | Profile, fundamentals, ratios, quote, history, dividends, or other |
| Operation | Concrete provider operation or endpoint family |
| Primary provider | `FMP` |
| Secondary provider | `Yahoo` |
| Trigger | Plan restriction, not found, empty response, missing field, invalid field, timeout, or service error |
| Primary status | HTTP/provider error code or successful-but-incomplete state |
| Requested fields | Fields expected from the primary response |
| Missing fields | Fields that triggered enrichment |
| Secondary outcome | Attempted, succeeded, failed, rejected, or partially accepted |
| Accepted fields | Exact Yahoo fields accepted by the platform |
| Persisted source | Effective provider for each persisted field or data category |
| Error detail | Sanitized diagnostic message without secrets or full sensitive payloads |
| Duration | Primary and fallback request duration |

### 2. Distinguish fallback from enrichment

Use separate event types:

- `PRIMARY_PROVIDER_FALLBACK`: the primary operation failed and the secondary provider supplied the result.
- `PRIMARY_PROVIDER_ENRICHMENT`: the primary operation succeeded but selected missing fields were requested from the secondary provider.
- `FALLBACK_ATTEMPT_FAILED`: the secondary provider was attempted but supplied no accepted value.

Do not report a fallback as successful merely because Yahoo was contacted. Success requires at least one accepted value.

### 3. Preserve field-level provenance

For merged objects, persist or expose the provider associated with each relevant field. At minimum, support provenance for:

- Company name, sector, industry, country, currency, exchange, market capitalization, description, and website
- Price, currency, change, change percentage, volume, and quote timestamp
- Fundamental and ratio periods used by valuation and scoring

If field-level persistence is considered too expensive, persist a structured provenance document attached to the ingestion event or snapshot.

### 4. Expose fallback details to ADMIN users

Add an ADMIN view and API filters for:

- Date/time range
- Symbol
- Job run
- Data category
- Trigger/reason
- Primary and secondary provider
- Attempt outcome
- Accepted versus rejected fallback values

The ADMIN job monitor and seed result should link directly to the corresponding fallback events. Summary cards should show:

- Fallback attempts and successful fallbacks
- Enrichment attempts and accepted enrichments
- Failure rate
- Top affected symbols and fields
- Counts grouped by FMP error code and endpoint

### 5. Make health reporting precise

Replace the ambiguous single `lastFallbackAt` state with separate fields:

- `lastFallbackAttemptAt`
- `lastSuccessfulFallbackAt`
- `lastEnrichmentAttemptAt`
- `lastSuccessfulEnrichmentAt`
- `fallbackAttemptsSinceStartup`
- `successfulFallbacksSinceStartup`
- `successfulEnrichmentsSinceStartup`
- `affectedSymbolsSinceStartup`

Health should become `DEGRADED` only according to an explicit policy. A successful optional enrichment should not necessarily have the same operational severity as a failed primary provider request.

## FMP Plan Validation Requirement

Because the current FMP plan is expected to cover platform requirements, each `PLAN_RESTRICTION` must be treated as a diagnosable anomaly until verified.

For every such event, capture enough sanitized information to verify:

- The FMP endpoint family and API version
- Request symbol and non-secret parameters
- HTTP status and provider error classification
- Whether the response truly indicates a plan restriction
- Whether an alternative supported FMP endpoint exists
- Whether the platform incorrectly maps empty or malformed data to `PLAN_RESTRICTION`
- Whether request frequency, concurrency, or quota behavior is being confused with plan coverage

Never log the FMP API key, authentication headers, Yahoo cookies/crumbs, or complete provider payloads containing unnecessary data.

## Expected ADMIN Workflow

1. Open the ADMIN market-data diagnostics page.
2. Select the startup seed job.
3. View all primary-provider failures and enrichment decisions.
4. Expand an event to see the ticker, data category, trigger, missing fields, Yahoo outcome, and accepted fields.
5. Filter to `PLAN_RESTRICTION` and verify whether the endpoint should be supported by the current FMP plan.
6. Export a sanitized diagnostic report suitable for provider support or internal debugging.

## Acceptance Criteria

- Every Yahoo call initiated by the FMP wrapper creates a persistent, correlated event.
- Events distinguish an attempt from a successful accepted fallback.
- Each event identifies symbol, operation, reason, providers, outcome, and accepted fields.
- Startup seed and background-job reports provide fallback counts and affected symbols.
- ADMIN users can inspect and filter fallback events without reading container logs.
- `ingestion_event.source` or an equivalent provenance structure reflects the effective provider instead of only the configured provider.
- A test proves that successful FMP data creates no fallback event.
- A test proves that missing exchange or volume creates an enrichment event with field-level provenance.
- A test proves that an FMP `PLAN_RESTRICTION` followed by successful Yahoo data creates a successful fallback event.
- A test proves that a failed Yahoo attempt is recorded but not counted as a successful fallback.
- No provider credentials, Yahoo session data, or unsafe raw payloads appear in logs or reports.
- The real-demo walkthrough can identify exactly which requested tickers and fields used Yahoo.

## Priority and Recommendation

Priority: **High for operational transparency and data provenance**.

Before treating Yahoo fallback as normal behavior, validate the observed FMP `PLAN_RESTRICTION` against the current plan and endpoint contract. Until that validation is complete, fallback should remain available for resilience but should be prominently visible to ADMIN users and monitored as an exception.

## Implementation Status

Implemented on branch `b4-yahoo-fallback-observability`:

- Dedicated `market_data_fallback_event` persistence with Flyway V22.
- Separate fallback and enrichment events with `SUCCESS`, `FAILED`, and `REJECTED` outcomes.
- Field-level evidence for missing and accepted profile/quote fields.
- Job name and job-run correlation through MDC.
- Sanitized, length-limited diagnostics without provider payloads or credentials.
- ADMIN event API: `GET /api/v1/admin/market-data-fallbacks`.
- ADMIN aggregate API: `GET /api/v1/admin/market-data-fallbacks/summary`.
- Filters for symbol, operation, event type, outcome, trigger, job run, and time range.
- ADMIN React analysis page at `/admin/fallbacks`.
- Failure-isolated persistence using a new transaction so observability cannot roll back ingestion.
