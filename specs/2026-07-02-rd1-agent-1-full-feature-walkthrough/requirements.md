# RD1-2 Requirements - Agent 1 Full Feature Walkthrough & Screenshots

## Scope

Phase RD1-2 validates the real-demo product experience delivered by RD1-1. It does not add new product endpoints or UI. It creates a repeatable evidence package for Agent 1, the prudent value investor persona from HD3, to walk every major feature in the platform using Yahoo Finance backed real-demo data.

The walkthrough covers:

- Auth: admin login, investor login, token refresh evidence, and logout.
- Dashboard: portfolio summary, top movers or current data cards, active alerts, and upcoming events where available.
- Seed and universe: additional seeding, Yahoo Finance source badges, and ingestion events.
- Job control: job list, run history, per-symbol events, enable/disable state, and scoped single-symbol run.
- Screener: Graham preset and conservative filters with MoS badges and business descriptions.
- Security detail: AAPL profile, financials, ratios, valuation, dividends, growth, insiders, and peers.
- In-depth review: KO review packet with DCF, FCF, Graham number, margin of safety, earnings, debt, dividend sustainability, historical charts, source coverage, and availability labels.
- Watchlist: JNJ and PG rationale notes for waiting for a better price, alert thresholds, and active alert visibility.
- Portfolio: a five-stock defensive portfolio, simulation, concentration warnings, and rebalance evidence.
- Alerts: create or confirm a price/MoS alert and verify dashboard visibility.
- Google sign-in visibility: login page and account settings show the integration without requiring live Google credentials.
- Account lifecycle: investor account settings and logout.

## Exclusions

- Group K, K1, K2, and K3 cloud deployment work is excluded by user instruction.
- No production data source migration, GCP infrastructure, commercial compliance hardening, or Terraform work.
- No live brokerage/order execution language or behavior.
- No committed secrets, JWTs, refresh tokens, provider payload dumps, or personal user data.

## Decisions

| Decision | Rationale |
|---|---|
| Evidence-first implementation | RD1-2 is a validation and demonstration phase following RD1-1, not a product feature phase. |
| PowerShell runner | The repository already uses PowerShell for local persona replay, and the user is on Windows. |
| Artifacts under the spec directory | Keeps replay output, report, and screenshot manifest tied to the phase and easy to review. |
| Screenshots are named by route and workflow | Stable names make stakeholder review and future replacement straightforward. |

## Assumptions

- The real-demo stack is started separately with `docker compose -f docker-compose.realDemo.yml up --build` before executing the walkthrough runner.
- The RD1-1 real-demo accounts exist: `admin@realdemo.local` / `admin` and `investor@realdemo.local` / `admin`.
- Yahoo Finance may return partial or rate-limited data; the report records provider limitations as evidence, not as product failure when workflows remain usable.
- Browser screenshots can be captured manually or by a browser automation harness against the same route list. This phase makes the manifest deterministic and stores the required output locations.

## Dependencies

- `scripts/real-demo-guide.md`
- `docker-compose.realDemo.yml`
- Frontend routes in `frontend/src/App.tsx`
- Existing authenticated REST APIs for auth, dashboard, seeding, job control, screener, security detail, review, watchlist, portfolio, alerts, and account pages.
