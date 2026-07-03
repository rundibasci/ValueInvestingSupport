# RD2-1 Agent 1 Curated Universe Walkthrough Report

Phase: RD2-1 - Agent 1 Curated Universe Walkthrough & Screenshots  
Persona: Agent 1, prudent value investor  
Data source: Yahoo Finance through the `realDemo` profile  
Boundary: Decision-support validation only; no buy/sell or personalised investment advice.

## Evidence Summary

| Area | Route or API | Expected Evidence | Status | RD1 Comparison |
|---|---|---|---|---|
| Universe selection | `/universe-curation`, `/api/v1/admin/universe/preview` | Defensive-quality template previews a focused, manageable research set. | Pending live replay | Replaces RD1 manual ticker entry. |
| Universe seeding | `/api/v1/admin/universe/seed` | Curated symbols are queued or seeded without manual CSV entry. | Pending live replay | Should reduce arbitrary symbol selection. |
| Ingestion monitoring | `/api/v1/admin/jobs/runs`, ingestion events | Per-symbol progress and provider/source evidence are visible. | Pending live replay | Same evidence family as RD1, applied to curated set. |
| Screener research | `/screener`, `/api/v1/screener` | Conservative preset or equivalent filters rank candidates by value score. | Pending screenshot | Research starts from curated universe rather than all seeded symbols. |
| Deep analysis | `/securities/{symbol}/review` | Top candidates expose valuation, FCF, earnings, debt, dividends, charts, and data-quality labels. | Pending screenshot | Confirms RD1 review packet works for curated candidates. |
| Comparison | `/api/v1/conservative-workflow/agent-one-comparison` | Top candidates can be compared on MoS, score, quality, leverage/liquidity, growth, dividend, and coverage. | Pending screenshot | Should make tradeoffs clearer than single-symbol review. |
| Portfolio construction | `/portfolio` | A 5 to 8 stock defensive portfolio can be simulated with concentration warnings. | Pending screenshot | Validates portfolio workflow against curated universe. |
| Watchlist rationale | `/watchlist` | Almost-cheap-enough symbols keep rationale notes and factual trigger conditions. | Pending live replay | Extends RD1 watchlist evidence with curation-derived ideas. |
| Dashboard and alerts | `/`, alert APIs | Dashboard reflects the new portfolio and watchlist threshold alerts. | Pending screenshot | Confirms curated research flows into monitoring surfaces. |

## Replay Command

```powershell
powershell -ExecutionPolicy Bypass -File scripts/rd2-agent1-curated-universe-walkthrough.ps1
```

Use `-SkipLiveApi` to verify artifact generation when the real-demo stack is not running.

## Findings Log

Populate this section after live replay with:

- workflow status,
- screenshot path,
- relevant API evidence file,
- data-quality or provider-coverage gaps,
- whether the gap blocks stakeholder demonstration,
- how the curated universe changed the research experience compared with RD1.

No finding should present the validation portfolio or watchlist as personalised advice.
