# H7 - Dashboard Validation

## Functional acceptance

- Any authenticated user can open the dashboard and sees only data permitted by backend ownership and authorization rules.
- The dashboard presents portfolio summary values, including total value, average margin of safety, and yield when data is available.
- Top movers are shown for the selected dashboard scope with clear labels for the period used to compute percent change.
- Active alerts are summarized with labelled severity/status badges, plain-language triggering conditions, and links into existing detail workflows.
- Upcoming earnings and dividend events for the next 30 days render when available, with clear empty and partial-data states.
- Dashboard items link to the relevant portfolio, watchlist, alert, and security-detail routes.
- Loading, empty, stale-data, partial-data, API-error, unauthorized, and expired-session states are clear and recoverable.
- Any surfaced valuation, margin-of-safety, recommendation, value-score, or alert-derived decision context retains the MiFID II decision-support disclaimer.

## Automated checks

- Frontend linting, TypeScript type checking, unit/component tests, and production build pass.
- Focused frontend tests cover:
  - dashboard summary rendering;
  - top-mover sorting and empty states;
  - active-alert badge labelling and navigation;
  - earnings/dividend calendar rendering for the next 30 days;
  - partial-data and API-error states;
  - disclaimer rendering where decision-support values appear.
- A deterministic integration or browser-level test verifies an authenticated dashboard journey without live FMP/Yahoo calls or secrets:
  1. authenticate as a non-admin test user;
  2. load a portfolio with holdings and local quote data;
  3. verify summary totals, margin of safety, yield, and top movers;
  4. verify active alerts and calendar events appear when seeded;
  5. navigate from dashboard cards into portfolio, watchlist, and security-detail routes.

## Manual review

- Check desktop and narrow layouts for scannability, stable panel dimensions, and no text overlap.
- Verify keyboard-only operation, focus order, accessible names, badge text, color contrast, and screen-reader-readable alert/calendar states.
- Confirm financial wording is descriptive and non-directive.
- Confirm no provider secrets, JWTs, refresh tokens, or sensitive user data appear in source, fixtures, local storage, logs, or rendered debug output.

## Merge criteria

- All automated checks pass, including production build and the deterministic dashboard journey.
- The dashboard works in a browser against local deterministic data.
- Existing H1-H6 routes, authentication behavior, query-client conventions, and shared UI patterns have no regressions.
- Feature-spec questions in `requirements.md` are resolved or explicitly deferred before implementation merge.
