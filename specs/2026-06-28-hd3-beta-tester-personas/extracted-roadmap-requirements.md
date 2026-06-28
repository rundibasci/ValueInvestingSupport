# HD3 Extracted Roadmap Requirements

Source activity: HD3 beta persona simulation against the local Docker demo, using three persona accounts, seeded demo data, portfolios, watchlists, and review workflows.

## Requirements To Carry Forward

1. Score availability transparency
   - Users must see whether a value score is available, stale, pending computation, blocked by missing provider data, or intentionally not computed.
   - Review, screener, search, seed results, and portfolio surfaces must use the same status vocabulary.
   - Seed workflows should either compute scores for newly seeded symbols or return a clear next step/status.

2. Portfolio concentration warnings
   - Portfolio detail and add-to-portfolio flows must flag concentration risk when a holding or sector dominates the model portfolio.
   - The warning must remain decision-support only: explain exposure and risk, do not tell the user to buy or sell.
   - Concentration signals should use persisted holdings, current prices, weights, and sector data when available.

3. Watchlist research rationale
   - Watchlist items must support a short user note describing why the symbol is being monitored.
   - Notes should capture target signals such as margin-of-safety threshold, valuation concern, data-quality gap, dividend concern, or narrative catalyst.
   - The watchlist UI should make "waiting for a better price" explicit for conservative workflows.

4. Screener empty-state diagnostics
   - When strict screener filters return no results, the UI must explain which filters are likely responsible.
   - The user should receive clear filter-relaxation suggestions without hiding the original criteria.
   - Empty-state diagnostics must work from local DB data and must not call external providers live.

5. Cross-symbol comparison
   - Users must be able to compare selected symbols on margin of safety, value score, quality metrics, leverage/liquidity, growth, dividend indicators, and source/data coverage.
   - The comparison surface should serve both disciplined value workflows and narrative-check workflows.
   - Missing or stale metrics must be visible at the cell or row level.

6. Story versus fundamentals review
   - The product should help a news- or trend-driven user compare a market narrative with fundamental valuation evidence.
   - The feature must not ingest live news by default during deterministic demos; it can use human-curated summaries or saved research notes.
   - Output should frame tensions, evidence, and missing data, not produce personalized investment advice.

7. Data-quality classification
   - Data-quality labels must distinguish provider limitation, stale provider data, missing seeded history, missing internal computation, and calculation guardrail failure.
   - These classifications should be available to frontend surfaces through structured API fields, not only display strings.
   - Demo validation should include symbols with complete data and symbols with partial coverage.

8. Persona replay scripts
   - The three HD3 persona workflows should become repeatable demo/regression scripts.
   - Scripts must recreate seed, review, portfolio, watchlist, and evidence-capture steps without relying on hidden manual state.
   - Reports should remain reproducible from documented source summaries and persisted demo data.

## Recommended Placement

- HD4 should select and implement the highest-value subset before Quality & Observability.
- Group I should add tests for selected beta-driven workflows and data-quality states.
- Mission and tech-stack specs should preserve the cross-cutting principles: score/data transparency, portfolio exposure clarity, research rationale capture, and deterministic demo evidence.
