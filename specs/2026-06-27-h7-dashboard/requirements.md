# H7 - Dashboard

## Purpose

Deliver the authenticated dashboard that helps users monitor the current state of their value-investing workflow after login: portfolio health, margin of safety, yield, active alerts, material portfolio movers, and near-term earnings/dividend events.

This phase supports the continuous monitoring step of the value-investing cycle described in `specs/mission.md`, while preserving the platform boundary as a decision-support tool rather than investment advice.

## Scope

- Add or complete the protected React dashboard route from H1.
- Show portfolio summary information: total value, average margin of safety, yield, and any existing portfolio concentration context that can be derived from local data.
- Show top movers in the selected portfolio using percent change from cached/local quote data.
- Show an active-alert summary with severity/status labels, triggering condition text, and links into the watchlist or security-detail flow.
- Show upcoming earnings and dividend calendar events for the next 30 days when supported by locally available data or by an explicitly scoped backend endpoint.
- Reuse the established frontend stack from `specs/tech-stack.md`: React 18, TypeScript strict mode, Tailwind CSS, TanStack Query, React Router, and existing API-client patterns.
- Preserve MiFID II decision-support disclaimers anywhere the dashboard surfaces fair value, margin of safety, recommendation, value score, or alert-derived investment context.

## Decisions

| Topic | Decision |
|---|---|
| Roadmap phase | This spec covers H7: Dashboard, following H6 and completing the frontend MVP dashboard surface for M9. |
| Primary user value | Make monitoring fast after login: users should immediately see portfolio condition, alert pressure, and near-term events. |
| Data source posture | Dashboard reads from authenticated application APIs backed by local DB/Redis. It must not call FMP/Yahoo directly from the frontend. |
| Layout | Use compact, operational dashboard panels optimized for scanning and repeated use, not a landing-page or marketing layout. |
| Navigation | Dashboard items should deep-link into existing portfolio, watchlist, alert, and security-detail routes. |
| Authorization | All authenticated roles may open the dashboard, with backend ownership/role checks defining visible data. |
| Advice boundary | Alert, margin-of-safety, value-score, and valuation language remains descriptive and non-directive. |
| Default scope | The first portfolio returned by the API is used by default; users can switch the dashboard scope from a portfolio selector. |
| Top movers | Top movers are ranked by absolute return versus holding cost basis because the current portfolio API exposes cost basis and current price. |
| Universe | H7 dashboard summaries use portfolio holdings only. Watchlist-only symbols remain in the Watchlist view. |
| Calendar | Upcoming earnings/dividend calendar is shown as an integration gap until a local backend endpoint exposes deterministic next-30-day data. |

## Context and guardrails

- This feature depends on H1-H6 frontend foundations and the portfolio/watchlist/alert capabilities built in Groups F, G, and H.
- The system's spine is Screening -> Fundamental Analysis -> Intrinsic Value Estimation -> Margin of Safety -> Recommendation -> Portfolio Construction -> Continuous Monitoring. H7 sits at monitoring, but it should link users back into the earlier research steps.
- Keep dashboard state in TanStack Query and avoid duplicating backend authorization or ownership logic in the client.
- Treat missing or stale financial data as a first-class state. Do not hide data-quality issues behind optimistic summary numbers.
- Do not expose provider secrets, raw credentials, refresh tokens, or personally scoped data in fixtures, logs, or UI debug output.
- Calendar and mover data should be deterministic in tests and must not require live FMP/Yahoo calls.

## Deferred feature-spec questions

1. Should a future iteration persist a user-selected default portfolio rather than using the first portfolio returned by the API?
2. Should a future backend dashboard endpoint expose previous-close movement, yield, sector concentration, and value-score averages as first-class metrics?
3. Should the next-30-day calendar include watchlist-only symbols once deterministic local calendar data is available?

## Out of scope

- New alert-detection rules, email delivery changes, real-time push updates, brokerage execution, order recommendations, or portfolio accounting/P&L.
- Google sign-in, observability, GCP deployment, and production compliance hardening.
- Live provider calls from the frontend or UI behavior that bypasses existing authenticated APIs.
