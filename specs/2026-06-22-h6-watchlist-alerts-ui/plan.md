# H6 — Watchlist & Alerts UI Plan

1. **Foundation and contracts**
   - Review the existing F1, G1, and G2 API contracts and the frontend conventions established in H1–H5.
   - Add typed client functions, query keys, protected routes, navigation, and resilient loading/error/empty states.

2. **Watchlist card experience**
   - Build responsive security cards with identity, supported valuation context, active-alert badges, and accessible actions.
   - Implement add, edit, and remove interactions with confirmation and query invalidation.
   - Link cards and alerts to the corresponding security-detail research route.

3. **Alert configuration and active-alert view**
   - Build React Hook Form controls for supported threshold and fundamental-degradation settings with clear validation.
   - Add an alert-focused view with filterable status, condition description, security context, and labelled severity/status badges.
   - Include the decision-support disclaimer wherever valuation/recommendation-derived data is surfaced.

4. **Integration and quality readiness**
   - Add deterministic integration coverage for login, watchlist creation/update, active-alert retrieval, and removal.
   - Add focused frontend tests for cards, form validation, badges, and error/empty states.
   - Run linting, type checks, tests, production build, and browser-level verification; fix accessibility and responsive-layout findings.
