# FI3 — Imported Portfolio Seed, In-Depth Analysis & Measurements

## Purpose

Turn a successfully committed portfolio import into a complete, traceable research run. Every resolved non-cash holding is refreshed through the shared research universe, receives the same analysis available on the Security Review page, and contributes to portfolio-level measurements. Cash remains a portfolio asset but is never treated as a security.

## Context

- FI1 persists previewed CSV positions and commits them to a user-owned portfolio.
- FI2 exposes import results, history, reconciliation, and the explicit entry point for analysis.
- DL5 already provides durable, bounded, idempotent asynchronous seed runs and progress polling.
- Existing valuation, Value Score, Security Review, and `PortfolioAnalyticsService` implementations are authoritative. FI3 orchestrates and extends their coverage; it does not create broker-specific or import-specific formulas.
- The supplied CSV contains coded securities and EUR/USD cash rows. Only resolved coded positions are eligible for security ingestion.

## Scope

### 1. Run initiation and identity

- Offer **Seed and analyze portfolio** after a successful import commit and from the portfolio detail page.
- `POST /api/v1/portfolios/{portfolioId}/analysis-runs` starts or joins an equivalent active run and returns `202 Accepted` with `analysisRunId`, status URL, outcome URL, and whether the caller joined an existing run.
- A run snapshots the committed import, holdings, quantities, broker prices/values, cash balances, and resolved symbols at submission. Later portfolio edits do not silently change that run; the UI marks its results superseded when the portfolio changes.
- The idempotency fingerprint includes the owner, portfolio, committed import, normalized resolved-symbol set, and analysis/calculation version. An equivalent queued/running run is joined. A later rerun refreshes stale data and calculations through normal upsert/version rules without duplicating holdings, securities, snapshots, valuations, or scores.
- Runs are durable and resume or reach an explained terminal state after process interruption. Work is bounded by configurable global and per-run concurrency and provider limits.

### 2. Ownership and authorization

- Only the authenticated portfolio owner can start, view, or retry its analysis runs. Cross-user identifiers return the repository's standard not-found response and disclose no metadata.
- The shared security universe and research snapshots remain shared; run metadata, imported values, holdings, cash, and portfolio analytics remain user-owned.
- No endpoint accepts an arbitrary user identifier, and logs/telemetry contain no tokens, secrets, or raw provider payloads.

### 3. Per-security orchestration

- Create one ordered outcome for every distinct resolved non-cash symbol. Multiple lots of a symbol share one security analysis but remain distinct inputs to position-value calculations.
- Reuse the shared ingestion pipeline for profile, annual and quarterly fundamentals, ratios, quote, dividends, insiders, and available estimates. FMP is primary and Yahoo Finance is fallback under the existing cache-first source policy.
- After ingestion, invoke the installed platform services needed to refresh the complete Security Review packet: DCF scenarios and sensitivity, WACC inputs, Graham, DDM eligibility, EPV, owner earnings, composite fair value and MoS; Value Score and its factors/guardrails; Piotroski, Altman, normalized/cyclical earnings and earnings quality; growth, profitability, returns, margins, liquidity, leverage, debt, dividend, moat, and capital-allocation measurements.
- The orchestration layer records calculation versions and eligibility/readiness, but never copies formulas into an import package. If a measurement is not installed or its inputs are insufficient, report it as unavailable.
- Outcome states are `queued`, `seeding`, `calculating`, `complete`, `partial`, or `failed`. Store source coverage by category, warnings, unavailable reasons, error summaries, and timestamps.
- One symbol failure never blocks another symbol. Retry failed/partial creates a correlated retry run containing only eligible outcomes while preserving completed results.

### 4. Availability, provenance, and freshness

- Every measurement exposes its provider/source categories, source dates, freshness/staleness, assumptions, calculation version, and eligibility guardrail where applicable.
- Use explicit unavailable reasons: unresolved security, missing history, model ineligible, provider-plan limitation, stale input, provider failure, or calculation failure. Missing values remain null/unavailable; zero is used only when it is a real sourced or calculated value.
- Retain the broker-source price and EUR value with their timestamp beside the refreshed platform quote/value. Show absolute and percentage variance without overwriting broker evidence.
- Do not persist raw provider payloads merely for FI3. Preserve existing immutable snapshot and cache behavior.

### 5. Portfolio measurements

- Start portfolio aggregation only after every security outcome is terminal (`complete`, `partial`, or `failed`). Calculate from successful eligible measurements and publish coverage denominators for every weighted result.
- Include current and base-currency value/weights, cash and currency exposure, weighted MoS, Value Score, yield and quality, holding/sector/country concentration, liquidity, benchmark comparison, and rebalancing diagnostics where the corresponding installed services and inputs support them.
- Cash contributes to total value, allocation, and currency exposure only. It is excluded from security seeding, valuation, scoring, concentration-by-security metrics, and Security Review links.
- Persist a versioned portfolio analytics snapshot linked to `analysisRunId`, its holding-input snapshot, calculation version, valuation timestamp, FX sources/dates, coverage, warnings, and partial status.
- Portfolio status may be `complete` with full required coverage, `partial` with explicit coverage gaps, or `failed` only when no usable aggregate can be produced.

### 6. API and UI contract

- Provide owner-scoped endpoints to start a run, read run status, page through symbol outcomes, obtain the latest run for a portfolio, and retry failed/partial outcomes.
- Status includes total/processed/succeeded/partial/failed counts, current phase, portfolio analytics status, created/started/completed/updated timestamps, and safe warnings/errors.
- Extend FI2 import result/history and the portfolio detail experience with the start action, resumable polling, phase/count progress, per-holding completeness, source/freshness badges, Security Review links, broker/platform reconciliation, portfolio-measurement freshness, and retry action.
- Poll using the established TanStack Query pattern with sensible backoff and stop on a terminal state. A page reload resumes from the latest active run.
- Fair value, MoS, scores, risk labels, benchmark/rebalance diagnostics, and recommendation-like text remain decision support. Show assumptions, data dates, limitations, and the existing MiFID II disclaimer.

## Decisions

- Use a dedicated `PortfolioAnalysisRun` plus ordered per-symbol outcomes. Reuse DL5 execution and idempotency patterns, but do not overload admin/shared-universe `SeedRun`, because FI3 has portfolio ownership, calculation, reconciliation, and aggregation lifecycle state.
- The action is explicit after commit; import commit itself remains fast and does not automatically consume provider quota.
- Use polling rather than SSE/WebSockets, consistent with the current React/TanStack Query stack.
- Analyze distinct symbols once per run and apply the resulting measurement to all matching lots.
- Capture a point-in-time input snapshot so results remain auditable when holdings later change.
- Continue through partial provider coverage and calculate portfolio results only after all symbol outcomes are terminal.

## Guardrails

- Data before opinion; provider facts, broker evidence, calculations, and interpretation remain visibly separate.
- Cache-first FMP/Yahoo behavior, rate limits, retries, timeouts, and circuit-breaking remain authoritative.
- Never fabricate, silently default, or present missing financial values as zero.
- Never give personalized investment advice or auto-execute trades.
- Database changes use additive Flyway migrations and preserve existing import and analysis records.

## Out of Scope

- New valuation, scoring, benchmark, FX, or rebalancing formulas not already supported by the platform.
- Automatic recurring portfolio refresh schedules, notifications, trade execution, tax-lot optimization, and broker synchronization.
- Editing imported holdings or resolving new symbols inside the analysis workflow.
- Cancellation and manual per-measurement overrides.
- FI4 PDF/CSV report export and shareable report artifacts.

