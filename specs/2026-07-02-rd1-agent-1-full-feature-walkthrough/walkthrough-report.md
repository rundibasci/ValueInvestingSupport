# RD1-2 Agent 1 Walkthrough Report

Phase: RD1-2 - Agent 1 Full Feature Walkthrough & Screenshots  
Persona: Agent 1, prudent value investor  
Data source: Yahoo Finance through the `realDemo` profile  
Boundary: Decision-support validation only; no buy/sell or personalised investment advice.

## Evidence Summary

| Area | Route or API | Expected Evidence | Status |
|---|---|---|---|
| Auth - admin | `/login`, `/auth/login` | Admin can authenticate with the real-demo account. | Pending live replay |
| Auth - investor | `/login`, `/auth/login` | Investor can authenticate with the real-demo account. | Pending live replay |
| Dashboard | `/` | Portfolio summary, alerts, and current data panels render without blocking errors. | Pending screenshot |
| Seed and universe | `/seed`, `/admin/seed` | Additional symbols can be seeded or queued; Yahoo source/freshness is visible where available. | Pending screenshot |
| Job control | `/admin/seed` and job APIs | Job status, history, and ingestion events are visible for startup and scoped runs. | Pending live replay |
| Screener | `/screener` | Graham/conservative filters show ranked rows with MoS and company context. | Pending screenshot |
| Security detail | `/securities/AAPL` | Profile, financials, ratios, valuation, dividends, growth, insiders, and peers are discoverable. | Pending screenshot |
| In-depth review | `/securities/KO/review` | DCF, FCF, Graham, MoS, earnings, debt, dividends, charts, source coverage, and availability labels are visible. | Pending screenshot |
| Watchlist | `/watchlist` | JNJ and PG rationale notes persist and keep "wait for better price" factual. | Pending live replay |
| Portfolio | `/portfolio` | Five-stock defensive portfolio, simulation, concentration warnings, and rebalance evidence are visible. | Pending screenshot |
| Alerts | Dashboard and alert APIs | Alert threshold or MoS alert appears in active-alert surfaces. | Pending live replay |
| Google sign-in visibility | `/login`, `/account` | Google sign-in and account linkage surfaces are present without requiring live Google credentials. | Pending screenshot |
| Account lifecycle | `/account`, logout | Investor can review account settings and end the session. | Pending screenshot |

## Required Screenshots

See `screenshots/README.md` for filenames, routes, persona, and redaction notes.

## Replay Command

```powershell
powershell -ExecutionPolicy Bypass -File scripts/rd1-agent1-walkthrough.ps1
```

Use `-SkipLiveApi` to verify artifact generation when the real-demo stack is not running.

## Findings Log

Populate this section after a live replay with:

- workflow status,
- screenshot path,
- relevant API evidence file,
- observed gaps,
- whether the gap blocks stakeholder demonstration.

No finding should present the validation portfolio as personalised advice.
