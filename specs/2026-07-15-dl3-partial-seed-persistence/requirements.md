# Requirements — DL3: Partial Seed Persistence Without Fabricated Valuation

## Purpose

Keep valid company and market data researchable when the valuation engine correctly determines that no valuation model is applicable. Today `SeedTickerService.seedOne` executes profile, fundamentals, ratios, quote, optional enrichment, valuation, score, and derived analytics inside one `REQUIRES_NEW` transaction. A `ValuationNotApplicableException` escapes the method and rolls back the successfully persisted market data, so a provider-valid company can appear completely unavailable.

DL3 separates ingestion success from analytical applicability while preserving the conservative guardrails in `specs/mission.md`. It uses the existing Spring Boot/JPA transaction model, availability vocabulary, React/TanStack Query UI, and provider provenance defined in `specs/tech-stack.md`.

## Scope

- Treat only `ValuationNotApplicableException` as a supported partial-seed outcome after core market data has been persisted.
- Commit valid security profile, fundamentals, ratios, price quote, dividends, insider data, provider provenance, and freshness already obtained for that ticker.
- Return a structured partial result with status `seeded_partial` and reason code `valuation_guardrail_blocked`.
- Keep `compositeFairValue`, `marginOfSafety`, `totalScore`, and `recommendation` null when valuation is not applicable.
- Preserve current full-success behavior when at least one valuation model produces a valid outcome.
- Preserve failure and rollback behavior for provider not-found, unusable required market data, persistence failures, and unexpected exceptions.
- Ensure retrying a partially seeded symbol is idempotent and can upgrade it to full success when later data supports a valuation.
- Make search, screener, security detail, and review surfaces distinguish a partial seeded security from a missing security or failed request.
- Show the partial outcome clearly in both seed result tables without presenting absent analytical values as zero.

## Existing Context

- `SeedService` normalizes a list and isolates failures per ticker by calling `SeedTickerService.seedOne`.
- `SeedTickerService.seedOne` uses `REQUIRES_NEW`, so each ticker already has an independent transaction.
- The method persists all market-data categories before calling `ValuationService.calculate`.
- `ValuationService` throws `ValuationNotApplicableException` only when every supported valuation model is unavailable after applying its guardrails.
- `SeedResult` already carries nullable valuation/score fields, `status`, source, fallback reason, refresh date, and error.
- The frontend already renders null price/valuation/score values as unavailable placeholders and exposes source coverage.
- The shared `AvailabilityStatus` vocabulary already includes `GUARDRAIL_BLOCKED`, `MISSING_SEEDED_HISTORY`, `MISSING_INTERNAL_COMPUTATION`, and `PROVIDER_LIMITED`.
- `SecurityReviewService` already has some guardrail-aware availability mapping, but all downstream surfaces must be verified against a security that exists without a valuation or score row.

## Result Contract

The existing seed endpoints retain their array response shape. A partial item is additive and resembles:

```json
{
  "symbol": "APD",
  "companyName": "Air Products and Chemicals, Inc.",
  "currentPrice": 282.10,
  "compositeFairValue": null,
  "marginOfSafety": null,
  "totalScore": null,
  "recommendation": null,
  "source": "profile:fmp,fundamentals:yahoo,ratios:fmp,quote:fmp",
  "status": "seeded_partial",
  "reasonCode": "valuation_guardrail_blocked",
  "reason": "Market data was saved, but no valuation model passed its eligibility guardrails.",
  "refreshedAt": "2026-07-15",
  "error": null
}
```

`reasonCode` is machine-readable and stable; `reason` is user-facing. `error` remains null because ingestion succeeded. Existing `failed` results continue using the failure field and do not masquerade as partial success.

## Decisions

### Catch at the transaction-owning service

`SeedTickerService.seedOne` catches `ValuationNotApplicableException` inside its `REQUIRES_NEW` boundary and returns a partial result. Because the supported exception no longer escapes the transactional proxy, successfully persisted market data commits. `SeedService` remains responsible for list iteration and unexpected per-symbol failure mapping.

### No synthetic valuation or score

DL3 does not relax DCF, Graham, DDM, EPV, or composite eligibility. It does not substitute current price, book value, sector multiples, zero, or a previous score as a new valuation. Without a current applicable valuation, every valuation-dependent field in the seed response is null.

### Existing historical analytical rows are not silently relabelled

A partial refresh must not present an older valuation or score as the result of the current seed. Existing historical rows remain immutable evidence and may still be shown by history-specific views with their own `dataAsOf`; the current seed response stays null and guardrail-blocked.

### Derived analytics are independent where safe

Risk, moat, and capital-allocation computations that do not require a newly created valuation may still run after the valuation guardrail outcome. `ValueScoreService.compute` is skipped when there is no current valuation result. Failure of unrelated analytics is not reclassified as `valuation_guardrail_blocked`.

### Partial securities remain active and discoverable

A provider-valid partial security remains `active=true`. Search and detail routes can return its saved company and market facts. Screener inclusion must follow the existing filter semantics: it may appear when requested filters do not require unavailable valuation/score fields, and must be excluded with explicit missing/guardrail diagnostics when a filter requires them.

### Provider failures remain failures

`MarketDataException.NOT_FOUND`, missing/unusable required core records, constraint violations, and unexpected exceptions keep the current failed result and rollback semantics. DL3 must not leave a new misleading active security for a genuine not-found request.

## Frontend Requirements

- Extend `SeedStatus` and result typing with `seeded_partial`, `reasonCode`, and `reason`.
- Count full and partial successes separately; do not count partial as failed or fully valued.
- Render a distinct partial badge and plain-language guardrail explanation.
- Keep unavailable fair value, MoS, score, and recommendation as `—`/unavailable, never `0`.
- Preserve provider and Yahoo fallback coverage for each successfully ingested category.
- Offer research navigation for the saved symbol while explaining that valuation-dependent screens or filters may remain unavailable.
- Apply the same semantics to direct seed and universe-curation result tables.

## Guardrails

- Never fabricate financial values, classifications, recommendations, or scores.
- Never catch generic valuation, persistence, or provider exceptions as partial success.
- Never label a partial seed as a complete analytical result.
- Preserve per-category provenance and freshness; do not replace platform facts with external estimates.
- Do not expose raw provider payloads, stack traces, credentials, API keys, or internal exception names.
- Preserve shared-universe authorization and existing per-ticker transaction isolation.
- Keep the decision-support disclaimer and avoid personalized buy/sell language.

## Out of Scope

- Relaxing or redesigning valuation formulas and eligibility rules.
- Asynchronous progress or polling for long ticker lists; that is DL5.
- Provider retry/fallback redesign or fallback observability changes.
- New security lifecycle states or a database column solely for partial seed state unless downstream semantics cannot be derived reliably.
- Backfilling every historical partial seed from prior executions.
- Treating optional dividend, insider, moat, or risk coverage gaps as core ingestion failure unless current behavior already does so.
- Changing screener financial filters to match null values.

## Resolved Feature-Spec Decisions

- Next roadmap phase: DL3.
- Partial status: `seeded_partial`.
- Stable reason code: `valuation_guardrail_blocked`.
- Transaction strategy: catch only the explicit applicability exception inside `SeedTickerService`.
- Valuation-dependent response values: null.
- Existing history: retained but not claimed as the current seed result.
- Discoverability: active/searchable, with structured downstream availability.
- Provider not-found and unexpected failures: unchanged rollback/failure behavior.

