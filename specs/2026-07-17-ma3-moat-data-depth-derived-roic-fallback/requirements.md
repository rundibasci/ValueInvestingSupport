# MA3 Moat Data Depth & Derived ROIC Fallback Requirements

## Purpose

Complete Phase MA3 by making moat analysis useful when a market-data provider supplies too little historical ROIC. The platform will persist the annual financial inputs needed to derive ROIC, calculate a transparent fallback during ingestion, and show the provenance of every annual observation without presenting an internal estimate as a provider fact.

This phase strengthens the mission principles of data before opinion, transparent calculations, conservative defaults, immutable history, and explainable missing data. It remains decision support and does not turn moat classifications into investment advice.

## Context

- MA1 classifies moat strength from annual ROIC observations and requires at least five usable years.
- Real-demo validation found that FMP stable ratio and key-metric endpoints can provide only one usable annual ROIC observation for symbols such as `INGR`, even when ten or more years of fundamentals exist.
- MA3 adds a derived path only when provider history is inadequate. Provider ROIC remains authoritative when at least five valid annual observations exist.
- The implementation must remain data-source agnostic after ingestion and operate from persisted platform-wide reference data.

## Included Scope

- Extend annual fundamental persistence with the historical inputs needed for ROIC derivation:
  - total equity;
  - total debt;
  - cash and cash equivalents;
  - operating income or EBIT;
  - effective tax-rate inputs or a documented tax-rate proxy;
  - available working-capital and invested-capital inputs useful for diagnostics.
- Populate those fields through the existing FMP and Yahoo adapters where the provider supplies them.
- Derive and persist annual ROIC during ingestion when fewer than five valid provider observations exist and sufficient annual inputs are available.
- Re-ingestion is the supported way to populate and recalculate existing symbols. Review requests must not trigger external provider calls or silently mutate historical financial snapshots.
- Extend the moat API and security review packet with a per-year ROIC series containing value, fiscal year/date, source, formula note, and structured availability or unavailable reason.
- Show per-observation provenance in the review UI, including the calculation rule and decision-support/data-methodology disclaimer.
- Retain `INSUFFICIENT_DATA` when fewer than five annual observations can be supported after provider and derived paths are evaluated.
- Add `INGR` regression coverage alongside deterministic fixtures for provider, derived, and insufficient-data paths.
- Add matching PostgreSQL and H2 Flyway migrations.

## Feature Decisions

### Derived ROIC rule

The approved baseline formula is:

- `NOPAT = EBIT × (1 − effective tax rate)`
- `invested capital = total equity + total debt − cash and cash equivalents`
- `derived ROIC = NOPAT ÷ average invested capital`

Average invested capital uses opening and closing annual invested capital when both are available. When only closing invested capital is available, the observation is not silently treated as equivalent: the calculation metadata must identify the single-period denominator, and the implementation must apply a conservative eligibility rule documented in code and API metadata.

The effective tax rate should be derived from persisted tax expense and pretax income when valid. Invalid, negative, or implausible provider tax rates must not flow through unchecked; a conservative configured proxy may be used only when its use is recorded in the observation metadata. Division by zero, non-positive invested capital, or missing core inputs produces an unavailable observation rather than a fabricated value.

### Source precedence

- Use provider history when at least five valid annual provider ROIC observations exist.
- Otherwise evaluate derived annual observations from persisted fundamentals.
- Do not mix sources invisibly. If a usable series combines provider and derived observations, each point retains its own source and the response identifies the series-selection rule.
- Supported sources are `FMP_RATIO`, `FMP_KEY_METRIC`, `DERIVED_INTERNAL`, and `UNAVAILABLE`; Yahoo-derived values use `DERIVED_INTERNAL` with provider/input metadata identifying Yahoo.

### Calculation timing and persistence

- Derivation occurs as part of ingestion or the existing post-ingestion analytical pipeline and is persisted for stable, auditable reads.
- Existing securities gain the deeper fields and derived observations through explicit re-ingestion/re-seeding.
- Repeated ingestion is idempotent under existing snapshot identity rules. Immutable historical snapshots are appended or reused according to existing repository conventions; they are not destructively overwritten.

### Provenance and disclaimer

Every annual ROIC observation exposed by the moat endpoint or review packet shows:

- fiscal year or observation date;
- normalized ROIC value when available;
- source enum;
- input-provider context;
- formula/method note;
- availability state and unavailable reason when applicable.

The UI exposes this provenance rather than hiding it behind only a summary badge. It also displays a concise disclaimer explaining that derived ROIC is an internal estimate based on reported financial inputs, may differ from provider or company calculations, and is decision-support information rather than investment advice.

## Functional Requirements

1. A symbol with at least five valid provider ROIC years uses provider history for moat classification.
2. A symbol with fewer than five provider observations but at least five eligible derived observations can receive a moat strength and trend classification.
3. A symbol with fewer than five observations after fallback remains `INSUFFICIENT_DATA` with a structured explanation.
4. Derived values use persisted inputs only and are reproducible from the returned methodology metadata.
5. Review and moat responses clearly distinguish provider values, internal derivations, and unavailable years.
6. Re-ingesting the same annual data does not create contradictory duplicate observations or change source precedence nondeterministically.
7. Existing MA1/MA2 consumers remain compatible, with new response fields added in a backward-compatible manner where practical.

## Guardrails

- Never manufacture missing accounting inputs or label an estimate as provider-supplied.
- Normalize provider percentage/decimal formats before validation and calculation.
- Preserve BigDecimal-based financial arithmetic with explicit precision and rounding.
- Reject non-positive denominators and record why the observation is unavailable.
- Keep provider access in the market-data/ingestion layer; review reads remain local DB/cache operations.
- Preserve authentication and existing `/api/**` authorization boundaries.
- Do not expose raw provider payloads, credentials, or secret configuration.
- Keep the moat label descriptive. No buy, sell, or suitability recommendation may be inferred from derived ROIC.

## Out of Scope

- Redesigning the overall moat classification thresholds introduced by MA1.
- Adding a new external financial-data provider.
- On-demand provider calls from the moat endpoint or review page.
- Generic restatement reconciliation or destructive rewriting of historical snapshots.
- Changing valuation, Value Score, portfolio allocation, or recommendation formulas.
- GCP deployment work from Group K.

## Dependencies

- MA1 moat calculation, persistence, endpoint, and review integration.
- MA2 review-page moat presentation.
- Existing FMP/Yahoo market-data adapters and seed/ingestion workflows.
- `FundamentalSnapshot`, `RatioSnapshot`, and their repositories.
- Spring Boot 3, Java 21, JPA/Hibernate, Flyway, PostgreSQL, H2 test migrations, React, TypeScript, TanStack Query, and Recharts as defined by `specs/tech-stack.md`.

## Open Implementation Detail

The exact persisted shape for per-year provenance may be a dedicated observation entity/table or an equivalently queryable normalized model. Implementation should prefer an auditable relational representation over opaque serialized blobs and must preserve the immutable-history principle.
