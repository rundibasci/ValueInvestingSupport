# FI3 — Implementation Plan

## 1. Confirm Contracts and Readiness Matrix

1. Inventory the FI1/FI2 committed-import model, DL5 seed-run machinery, shared ingestion services, Security Review composition, installed valuation/score/risk services, and portfolio analytics outputs.
2. Define the security-analysis readiness matrix: required inputs, terminal eligibility, unavailable reasons, provenance fields, and calculation version for every installed measurement family.
3. Finalize owner-scoped request/response DTOs, status transitions, retry semantics, idempotency fingerprint, input snapshot, and portfolio coverage rules.
4. Document which roadmap measurements are already implemented and identify explicit unavailable states for capabilities not installed; do not add substitute formulas.

## 2. Add Durable Analysis-Run Persistence

1. Add an additive Flyway migration for portfolio analysis runs, immutable run inputs/cash inputs, ordered symbol outcomes, outcome source/availability metadata, and run-to-portfolio-analytics linkage.
2. Add JPA entities and repositories with owner/portfolio scoping, active-fingerprint lookup, atomic run claim, terminal counts, retry correlation, and stale-run recovery queries.
3. Store normalized symbols and snapshots deterministically, enforce uniqueness needed for idempotency, and index active/status/portfolio lookups.
4. Add repository and migration tests for ownership, uniqueness, state transitions, and persistence compatibility.

## 3. Build the Per-Security Analysis Orchestrator

1. Introduce a portfolio analysis coordinator with a bounded executor and configuration modeled on DL5.
2. Submit every distinct resolved non-cash symbol to the shared ingestion path; explicitly reject cash and unresolved rows from security work while retaining them in the input snapshot.
3. Add a `SecurityAnalysisOrchestrator` that invokes installed valuation, score, risk/quality, and Security Review services after seeding and returns a normalized completeness/provenance summary.
4. Implement state transitions, processed counters, per-category source coverage, safe errors, partial completion, and process-restart recovery.
5. Implement active-run joining and idempotent refresh/upsert behavior so reruns create no duplicate analytical records.

## 4. Calculate and Persist Portfolio Measurements

1. Extend the portfolio analytics input model to consume the run's holdings, cash, broker evidence, refreshed quotes, FX evidence, and terminal security outcomes.
2. Trigger aggregation once, only after all symbol outcomes are terminal, while allowing explicit partial coverage.
3. Reuse `PortfolioAnalyticsService` and installed benchmark/rebalancing services for value/weights, cash/currency exposure, weighted measures, concentration, liquidity, comparison, and diagnostics.
4. Persist a versioned analytics snapshot with per-metric coverage, provenance, freshness, warnings, and `analysisRunId`.
5. Add reconciliation calculations that preserve broker values and expose refreshed value variance.

## 5. Expose Owner-Scoped Analysis APIs

1. Implement start/join, latest, status, paginated outcomes, and retry-failed/partial endpoints under `/api/v1/portfolios/{portfolioId}/analysis-runs`.
2. Return `202 Accepted` for submission/retry and stable links for polling and outcomes.
3. Apply standard validation, problem responses, page limits, authentication, owner-not-found behavior, and safe error redaction.
4. Add controller/service tests for lifecycle, active joining, snapshot behavior, ownership isolation, retry selection, and cash exclusion.

## 6. Add Import and Portfolio Analysis UI

1. Add strict TypeScript API types, client functions, and stable TanStack Query keys for runs, outcomes, latest status, and retry.
2. Enable **Seed and analyze portfolio** on successful committed-import results/history and portfolio detail; prevent duplicate submissions while a mutation is pending.
3. Add a reusable progress panel with lifecycle phase, counts, resumable polling, terminal stopping, partial/error summaries, and retry failed/partial.
4. Show per-holding completeness, source/freshness, unavailable reasons, broker/platform price and value variance, and links to the full Security Review page.
5. Show portfolio measurements with coverage and freshness, cash/currency treatment, assumptions, limitations, and the existing decision-support disclaimer.
6. Add loading, empty, stale/superseded, partial, failed, reload-resume, and narrow/mobile states with accessible labels and keyboard behavior.

## 7. Verify the End-to-End Workflow

1. Add backend unit and integration coverage for import → commit → seed → calculate → Security Review → portfolio analytics.
2. Cover mixed FMP/Yahoo data, provider-plan limitations, cash, duplicate lots, one-symbol failure, missing history, partial aggregate coverage, retry, idempotent rerun, recovery, and ownership isolation.
3. Add frontend API/component/page tests for start, polling, reload resume, terminal states, reconciliation, review links, retry, and disclaimer visibility.
4. Run targeted backend tests, the broader backend suite, frontend tests/typecheck/build, Flyway validation, and `git diff --check`.
5. Record evidence and any accepted environment limitation in `validation.md`; merge only when all required criteria pass.

