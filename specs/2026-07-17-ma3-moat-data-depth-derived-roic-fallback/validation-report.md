# MA3 Validation Report

## Result

Phase MA3 is implemented and locally verified.

## Delivered

- Persisted annual pretax income and income-tax expense alongside existing EBIT, equity, debt, and cash history.
- Added normalized, auditable annual ROIC observations with source, input-provider context, formula notes, and structured unavailable reasons.
- Added ingestion-time provider precedence and derived fallback using NOPAT divided by average opening/closing invested capital.
- Kept review reads provider-free; moat classification consumes only persisted ROIC observations.
- Extended moat and review responses with per-year provenance and the approved methodology/decision-support disclaimer.
- Updated the review UI to chart the persisted series and display provenance for every annual point.
- Added calculator, provider-precedence, derived-fallback, and true-unavailable regression tests using `INGR` as the fallback-shaped symbol.

## Verification

- Focused backend MA3, moat, seed, and persistence tests: passed.
- Complete backend Maven test suite: passed.
- Frontend TypeScript check: passed.
- Frontend production build: passed.
- H2 entity/schema compatibility was exercised by repository and full-suite tests.
- `git diff --check`: required as the final repository check.

## Environment Note

No live FMP call was required. The default regression suite uses deterministic fixtures and introduces no credentials or raw provider payloads.
