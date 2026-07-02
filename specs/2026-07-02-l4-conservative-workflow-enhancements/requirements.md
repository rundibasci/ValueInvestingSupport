# L4 Conservative Workflow Enhancements - Requirements

## Scope

Phase L4 adds conservative research workflow enhancements traceable to the Agent 1 prudent-value journal:

- A conservative research preset combining positive margin of safety, score availability, dividend coverage, leverage/liquidity resilience, and data completeness.
- Screener empty-state diagnostics for conservative filters that identify likely eliminating criteria and suggest relaxations while preserving current criteria.
- Selected-symbol comparison for the Agent 1 workflow across margin of safety, value score, quality, leverage/liquidity, growth, dividend indicators, and source/data coverage.
- Replay evidence showing how the 10-stock validation portfolio can be recreated from seeded/local data and reviewed for concentration and data quality.

## Exclusions

- Group K, K1, K2, and K3 cloud distribution work is explicitly excluded.
- No brokerage, order, buy/sell, or personalized investment-advice language may be added.
- No live FMP or Yahoo Finance dependency is required for deterministic tests.
- Broader saved research notes are deferred unless the existing watchlist rationale field is demonstrably too small.

## Decisions

- Preset and diagnostics metadata will be deterministic backend application data so tests do not depend on provider availability.
- Empty-state diagnostics will explain likely filter pressure and provide relaxation options without changing filters automatically.
- Comparison support will use a small, stable DTO that can be rendered by the frontend and later backed by richer live data.
- The implementation may add narrowly scoped frontend fixtures when backend integration is not necessary for build-time validation.

## Assumptions

- The earliest unfinished roadmap phase is L4; L1, L2, and L3 are already complete on `main`.
- Existing watchlist rationale support is enough for "wait for better price" and data-quality-gap workflows, so no new persistence field is planned.
- The current screener UI can accept a conservative preset as UI state even if all backend screener filters are not yet equally expressive.
- Selected-symbol comparison can be deterministic for Agent 1 evidence while still matching the real data shape expected by future live comparison.

## Dependencies

- Mission principles 2, 4, 8, 12, 13, and 14.
- Tech-stack frontend surfaces for screener diagnostics, cross-symbol comparison, watchlist rationale, and score/data-quality transparency.
- Roadmap Group L acceptance checklist and Agent 1 symbol set: `BRK.B, JNJ, PG, KO, PEP, WMT, MSFT, ADP, UNP, XOM`.
