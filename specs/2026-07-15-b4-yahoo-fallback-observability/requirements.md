# Requirements - Phase B4: Yahoo Fallback Observability

## Purpose

Make every Yahoo Finance invocation made by the FMP wrapper visible, durable, and analyzable by ADMIN users. The current FMP plan is expected to cover normal platform ingestion, so fallback and enrichment must be treated as explicit operational evidence rather than silent behavior.

## Scope

- Persist a separate market-data fallback event for explicit fallback, enrichment, and failed secondary-provider attempts.
- Correlate events with symbol, operation, trigger, job name, job run ID, providers, outcome, accepted fields, duration, and sanitized error detail.
- Instrument FMP-to-Yahoo profile, fundamentals, ratios, and quote paths, including exchange and volume enrichment.
- Add ADMIN-only APIs for paginated filtering and aggregate analysis.
- Add an ADMIN React page for counts, filters, and event inspection.
- Preserve the existing market-data response behavior and resilience policy.

## Decisions

- Use a new `market_data_fallback_event` table rather than overloading ingestion events whose semantics describe job data outcomes.
- Record one completed event per Yahoo attempt. `SUCCESS` requires at least one accepted Yahoo field; mere contact is not success.
- Use event types `PRIMARY_PROVIDER_FALLBACK` and `PRIMARY_PROVIDER_ENRICHMENT`, with outcomes `SUCCESS`, `FAILED`, or `REJECTED`.
- Persist field names and sanitized diagnostics, never provider payloads, keys, authentication headers, cookies, or Yahoo crumbs.
- Expose endpoints under `/api/v1/admin/market-data-fallbacks`; existing security rules keep `/api/v1/admin/**` ADMIN-only.
- Provide filters for symbol, operation, event type, outcome, trigger, and job run.

## Guardrails

- Observability persistence must not replace provider data or change valuation/scoring behavior.
- Failure to write an observability event must be logged but must not make an otherwise successful market-data request fail.
- Normalize symbols and enum-like values before persistence.
- Sanitize and length-limit provider error messages.
- Do not infer that `PLAN_RESTRICTION` is legitimate; record enough context for later plan/endpoint validation.

## Out of Scope

- Storing full FMP or Yahoo response bodies.
- Automatic disabling of Yahoo fallback.
- Changing the FMP subscription or endpoint catalog.
- Alert delivery or Prometheus dashboards; the ADMIN page is the analysis surface for this phase.
- Backfilling historical fallback events that were not previously captured.

