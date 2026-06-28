# HD3 - Beta Tester Persona Simulation Plan

1. **Prepare runtime and research inputs**
   - Review HD2 demo-readiness notes, localstack credentials, demo URLs, seed workflows, and known limitations.
   - Prepare deterministic source-summary fixtures for the three personas: Seeking Alpha-style, Morningstar-style, and Google News-style summaries.
   - Define candidate ticker lists for each persona, including which symbols are expected to be pre-seeded and which may be newly seeded during the workflow.
   - Start the full local demo stack and confirm backend health, frontend availability, login, seed universe, screener, review, watchlist, and portfolio routes are reachable.

2. **Persona 1: Very prudent value investor**
   - Use conservative value-investor summaries to choose defensive candidates.
   - Seed or refresh selected symbols as needed.
   - Screen for margin of safety, valuation conservatism, debt/liquidity resilience, dividend coverage, and data freshness.
   - Review candidate security detail and in-depth review pages.
   - Build a small defensive model portfolio only when the platform data supports enough margin of safety.
   - Create a watchlist for symbols that are attractive businesses but not cheap or complete enough for portfolio inclusion.
   - Document trust signals, data gaps, and conservative-user friction.

3. **Persona 2: Hedge-fund asset allocator**
   - Use allocator-style analyst-note summaries to choose scalable, high-margin, quality-business candidates.
   - Seed or refresh selected symbols as needed.
   - Compare valuation, quality metrics, margin of safety, portfolio concentration risk, sector exposure, and data completeness.
   - Build a higher-conviction model portfolio with explicit position sizing rationale.
   - Create a watchlist for candidates awaiting better valuation, cleaner data, or lower concentration risk.
   - Document whether the platform supports professional scanning, comparison, and portfolio construction.

4. **Persona 3: Financial journalist / trend observer**
   - Use headline-style source summaries to choose trending or narrative-driven candidates.
   - Seed or refresh selected symbols as needed.
   - Test whether the platform challenges or validates market narratives with fundamentals, valuation, and data-quality context.
   - Build a news-driven model portfolio when the persona would plausibly track a theme.
   - Create a watchlist for fast-moving stories, narrative risks, or symbols needing better fundamental confirmation.
   - Document whether the platform helps convert narrative interest into structured research.

5. **Report writing and findings synthesis**
   - Write one report per persona under this spec directory.
   - Include source summaries, seed actions, candidate selection, portfolio output, watchlist output, usability impressions, and validation evidence.
   - Create a combined findings index that deduplicates issues across personas and groups recommendations by blocker, product gap, UX polish, data-quality concern, and nice-to-have.
   - Identify which issues should feed HD4 feature selection versus later quality, observability, identity, cloud, or commercial-readiness phases.

6. **Validation and merge readiness**
   - Preserve full-demo evidence for each persona: routes, symbols, seed results, review outputs, portfolio/watchlist results, and any screenshots or API snippets.
   - Run frontend typecheck/build and backend compile/tests where supported if any code or fixture changes are made.
   - Run `git diff --check`.
   - Confirm reports are reproducible from documented summaries and seeded/localstack data.
   - Stop any local demo services started during validation.
