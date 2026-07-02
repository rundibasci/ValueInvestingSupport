# L3 Availability Status Examples And Diagnostics - Requirements

## Scope

Phase L3 adds deterministic examples and diagnostics for every shared availability state used by security review, screener, portfolio, and watchlist-adjacent research flows:

- `AVAILABLE`
- `STALE`
- `PENDING`
- `PROVIDER_LIMITED`
- `MISSING_SEEDED_HISTORY`
- `MISSING_INTERNAL_COMPUTATION`
- `GUARDRAIL_BLOCKED`

The implementation must reuse the existing `it.mazzoni.vis.common.AvailabilityStatus` model and avoid introducing a second status vocabulary.

## Exclusions

- Group K, K1, K2, and K3 cloud distribution work is explicitly excluded.
- No live FMP or Yahoo Finance calls are required.
- No investment recommendation or buy/sell language may be added.
- No persistence migration is expected unless implementation proves it unavoidable.

## Decisions

- Deterministic examples will be produced by backend application code, not by fragile external scripts.
- Diagnostics will explain how each status should be interpreted by conservative users while preserving the decision-support boundary.
- Existing review-page availability badges will be updated only as needed to render all shared states consistently.
- Tests will focus on status coverage, reason text, and UI status classification rather than full end-to-end seeded market data.

## Assumptions

- The repository already completed L1 and L2, so review and portfolio flows can consume availability metadata.
- `PENDING` may represent queued or not-yet-computed data even if the current production endpoints rarely emit it.
- `STALE` can be demonstrated by deterministic example metadata rather than relying on the 7-day stale guard throwing a request error.
- Watchlist-adjacent coverage means links and review context around watchlist symbols, not a new watchlist persistence model.

## Dependencies

- Mission principle 12 requires missing data to be explainable.
- Roadmap L3 requires deterministic examples or documented reasons for every status.
- Existing frontend surfaces include `SecurityReviewPage`, `ScreenerPage`, `PortfolioPage`, and `WatchlistPage`.
