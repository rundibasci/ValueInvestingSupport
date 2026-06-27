# H4B - Review Page Portfolio-Add Integration Plan

1. **Scope alignment and contract review**
   - Review the H4A `SecurityReviewPage` implementation, especially the disabled add-to-portfolio action, action-bar layout, route protection, data-loading states, and existing tests.
   - Review H5 portfolio API client, query keys, mutations, form patterns, portfolio selectors, holding DTOs, ownership/error handling, and cache invalidation.
   - Confirm the backend holding-create contract: required fields, default quantity/cost behavior, duplicate response, portfolio ownership errors, and validation errors.
   - Keep H4B constrained to wiring the review page to existing portfolio behavior; do not add new backend endpoints unless an existing contract gap blocks the feature.

2. **Portfolio selection and empty states**
   - Replace the disabled "coming soon" control with an accessible add-to-portfolio trigger.
   - Load the authenticated user's portfolios through the existing portfolio-list query.
   - Show a portfolio picker that lists only portfolios returned by the authenticated API.
   - Provide a clear empty state when no portfolios exist, with a path to the existing Portfolio page or shared portfolio-create flow.
   - Preserve the review-page action layout, source/freshness labels, valuation disclaimer, and inline custom DCF controls.

3. **Add-holding mutation**
   - Submit the reviewed symbol to the selected portfolio through the existing holding-create or holding-update API contract.
   - Include only fields supported by the existing API. If quantity, target weight, or cost basis is required, use the established H5 defaults or prompt for the required field with a compact form.
   - Invalidate or refresh affected portfolio queries after success, including portfolio detail, holdings, dashboard summaries if already wired, and any review-page state that reflects portfolio membership.
   - Show success feedback with the portfolio name and symbol.
   - Handle duplicate-holding outcomes explicitly according to backend behavior: rejected duplicates show a recoverable message; merge/update behavior shows the resulting holding state.

4. **Authorization, errors, and resilience**
   - Respect backend authorization, ownership, validation, not-found, duplicate, stale-session, and network errors.
   - Ensure a user cannot select or mutate portfolios that are not returned by the authenticated API.
   - Keep already loaded review-page research data visible when portfolio queries or mutations fail.
   - Ensure expired-session handling follows the existing auth flow and does not lose the intended review-page return path.
   - Avoid storing sensitive tokens or portfolio mutation payloads in local storage, URLs, logs, or rendered raw JSON inspectors.

5. **Tests and merge readiness**
   - Add focused frontend tests for portfolio loading, empty state, portfolio selection, successful add, duplicate handling, validation errors, unauthorized/expired-session behavior, and cache invalidation.
   - Add regression coverage that the review packet still renders DCF, FCF, Graham number, margin of safety, earnings, debt, dividends, quick ratio where available, source coverage, freshness, and unavailable-data labels.
   - Add a deterministic browser or integration journey: authenticate as a non-admin investor, open `/securities/AAPL/review`, select an owned portfolio, add the symbol, and verify the holding appears in the portfolio view.
   - Run linting, TypeScript checks, tests, production build, and a responsive/manual accessibility check for the add-to-portfolio flow.
   - Confirm financial wording remains descriptive and non-directive.
