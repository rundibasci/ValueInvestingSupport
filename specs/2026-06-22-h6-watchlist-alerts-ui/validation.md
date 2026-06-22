# H6 — Watchlist & Alerts UI Validation

## Functional acceptance

- Any authenticated user can open the watchlist area and sees only watchlist data permitted by the backend.
- Users can add, edit, and remove watchlist entries and configure every alert setting supported by the existing API.
- Watchlist entries render as responsive cards with clear security identity, actions, and active-alert state.
- Active alerts are easy to identify through labelled badges and plain-language condition text; colour is not the only signal.
- Users can open an alert-focused view, distinguish active from other available states, and navigate to the associated security detail.
- Loading, empty, validation, mutation-pending, API-error, and unauthorized/session-expired states are clear and recoverable.
- Every surfaced valuation, margin-of-safety, recommendation, or alert-derived decision context retains the MiFID II decision-support disclaimer.

## Automated checks

- Frontend linting, TypeScript type checking, unit/component tests, and production build pass.
- A deterministic integration test verifies this journey without live FMP/Yahoo calls or secrets:
  1. authenticate as a non-admin test user;
  2. create a watchlist entry with supported alert thresholds;
  3. update its alert configuration and verify persistence;
  4. retrieve an active alert associated with the security and verify its status/condition;
  5. remove the watchlist entry and verify the resulting state.
- Focused frontend tests cover card rendering, labelled alert badges, form validation, query invalidation, and empty/error states.

## Manual review

- Check narrow and desktop layouts, keyboard-only operation, focus management, accessible names, contrast, and screen-reader-readable alert state.
- Confirm alert wording is informative rather than directive and that no secrets or personally scoped data leak through UI state or test fixtures.

## Merge criteria

- All automated checks pass, including the integration test and production build.
- The authenticated watchlist-and-alert flow works in a browser against deterministic local data.
- Existing H1–H5 routes, authentication behavior, and shared API-client conventions have no regressions.
- Scope remains limited to H6; review feedback is resolved before merge.
