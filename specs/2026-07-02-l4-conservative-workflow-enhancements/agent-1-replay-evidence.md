# Agent 1 Replay Evidence - Conservative Workflow Enhancements

## Symbol Set

`BRK.B, JNJ, PG, KO, PEP, WMT, MSFT, ADP, UNP, XOM`

## Implemented Traceability

- Conservative research preset: exposed by `GET /api/v1/conservative-workflow/preset` and available in the screener preset controls as `conservative`.
- Empty-state diagnostics: exposed by `POST /api/v1/conservative-workflow/empty-state-diagnostics`; the screener renders likely filter pressure and relaxation options without changing the active filters.
- Selected-symbol comparison: exposed by `GET /api/v1/conservative-workflow/agent-one-comparison`; the screener renders the Agent 1 symbol set across valuation, score, quality, resilience, growth, dividend, and source/data coverage.
- Watchlist rationale: existing monitoring reasons already include `WAIT_FOR_BETTER_PRICE` and `DATA_QUALITY_GAP`, so broader saved research notes are deferred.
- Availability status coverage: L3 deterministic diagnostics remain the source of truth for all shared availability states.

## 10-Stock Portfolio Recreation

The L1 and L2 replay packs already document equal-weight validation portfolio construction and concentration/data-quality review. L4 adds a comparison and filtering workflow that can be used before opening each review packet or rebuilding the validation portfolio from seeded/local data.

## Deferred Follow-Ups

- Live screener filtering can later deepen dividend coverage and data-completeness criteria beyond the deterministic L4 metadata.
- Broader saved research notes remain deferred because the existing 500-character watchlist rationale field covers the current "wait for better price" and data-quality-gap workflows.

## Decision-Support Boundary

The new preset, diagnostics, and comparison copy describe research criteria and evidence gaps. They do not present the 10-stock model as personalised investment advice.
