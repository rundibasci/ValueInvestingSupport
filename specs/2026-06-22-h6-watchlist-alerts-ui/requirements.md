# H6 — Watchlist & Alerts UI

## Purpose

Deliver the authenticated React interface for monitoring investment candidates after analysis: users can maintain a personal watchlist, see active alert conditions at a glance, and manage threshold-based alerts. This implements the monitoring step of the value-investing cycle without turning the product into investment advice.

## Scope

- Add protected watchlist and alerts routes, navigation, typed API-client functions, and TanStack Query hooks for the existing Group F and G endpoints.
- Present each watched security as a responsive card containing its identity, available valuation context, active-alert status, and clear actions.
- Support creating, updating, and removing watchlist items, including supported margin-of-safety and fundamental-degradation alert settings.
- Provide an alert-focused view for active alerts, including the triggering condition, severity/status badge, affected security, and a route back to its research detail.
- Support all authenticated roles (`ADVISOR`, `INVESTOR`, and `ADMIN`) while relying on backend authorization and ownership enforcement.
- Add integration coverage for the core authenticated watchlist and alert journey using deterministic test data.

## Decisions

| Topic | Decision |
|---|---|
| Feature breadth | Go beyond the roadmap minimum with editing of watchlist alert settings, alert-focused filtering/status presentation, and direct links into security research. |
| Layout | Use responsive cards for watchlist securities; retain an accessible structured alternative for dense alert information where useful. |
| Alert communication | Use a labelled badge for alert state/severity plus text describing the condition. Colour supports recognition but is never the sole signal. |
| Access | All authenticated roles can manage their own watchlists and view their own alerts. The frontend respects API responses rather than recreating authorization logic. |
| Data flow | Reuse existing watchlist and alert APIs; no backend endpoint, alert rule, notification channel, or scheduler changes are part of H6. |
| Frontend stack | React 18, TypeScript strict mode, Tailwind CSS, TanStack Query, React Hook Form, and React Router, in line with `specs/tech-stack.md`. |
| Validation | Include a deterministic integration test covering authenticated watchlist mutation and active-alert display, plus frontend checks and a production build. |

## Context and guardrails

- This is roadmap Phase H6, following F1, G1, G2, and frontend phases H1–H5.
- The platform is decision support. Where cards show valuation, margin-of-safety, recommendation, or alert-derived investment context, preserve the MiFID II disclaimer and avoid imperative trading language.
- Keep remote state in TanStack Query and form state in React Hook Form; use the existing API-client layer and invalidation patterns after mutations.
- Alert copy must say what condition triggered and when available, not imply urgency or a trade instruction.
- Cover loading, empty, mutation-pending, validation, API-error, unauthorized, and expired-session states with recoverable UI behavior.
- Never put credentials, tokens, or external data-provider secrets into frontend source or fixtures.

## Out of scope

- New alert-detection rules, delivery channels, notification preferences, real-time push/websocket updates, new backend endpoints, or scheduler changes.
- Dashboard aggregation (H7), brokerage execution, portfolio accounting, or any personalized investment recommendation.
