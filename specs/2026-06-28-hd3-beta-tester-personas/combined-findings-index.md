# HD3 Combined Findings Index

## Blockers

### Fixed During HD3: Seed Transaction Boundary

- Severity: Blocker.
- Surface: `POST /api/v1/universe/seed` and `POST /api/v1/admin/seed`.
- Observed behavior: reseeding existing localstack symbols returned per-symbol errors: `No EntityManager with actual transaction available for current thread - cannot reliably process 'remove' call`.
- Expected behavior: seed operations that replace current/TTM rows must run inside an active transaction.
- Resolution: added `@Transactional` to `SeedService.seedTickers`.
- Validation: `SeedServiceTest` passed; retrying persona seed lists returned successful rows for KO, JNJ, PG, MSFT, NVDA, ADBE, and TSLA.

## Product Gaps

- Conservative persona: no explicit "research note / wait for better price" workflow beyond watchlist.
- Allocator persona: portfolio concentration is calculated but not strongly warned in the portfolio detail workflow.
- Allocator persona: newly seeded symbols should have an explicit score computation or score-pending state.
- Journalist persona: no "story versus fundamentals" report or comparison mode.
- Cross-persona: strict screener filters can return empty results without explaining which criteria eliminated candidates.

## UX Polish

- Highlight conflicts between high quality/score and negative margin of safety.
- Make missing value score states more prominent on review and screener workflows.
- Improve screener empty-state copy with filter relaxation guidance.
- Add clearer post-seed handoffs for newly seeded symbols with partial data coverage.

## Data-Quality Concerns

- Newly seeded PG, NVDA, ADBE, and TSLA produced useful valuation/review data but did not have value scores.
- Newly seeded symbols often had fewer annual financial rows than localstack seed symbols.
- Data-quality notes are useful but should distinguish provider limitation, not-yet-computed internal metric, and stale data.

## Nice-To-Have Enhancements

- Conservative preset combining positive MoS, dividend coverage, low leverage, and data completeness.
- Selected-symbol comparison table for MoS, score, ROIC, debt, growth, and source coverage.
- Saved research notes attached to watchlist symbols.
- Persona-style demo scripts that can replay common investor workflows.

## HD4 Input

Recommended candidates for HD4 feature selection:

1. Score availability transparency for newly seeded symbols.
2. Portfolio concentration warnings in portfolio detail and add-to-portfolio flows.
3. Screener empty-state diagnostics.
4. Research note/watchlist rationale support.
5. Cross-symbol comparison table for allocator and journalist workflows.
