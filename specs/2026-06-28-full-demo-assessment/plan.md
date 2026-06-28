# HD1 - Full Demo UI Assessment Plan

1. **Environment and route inventory**
   - Review current startup scripts, README guidance, package scripts, Docker Compose files, localstack/full-demo profiles, seeded credentials, and deterministic fixture/data paths.
   - Start the required backend/demo services and React frontend using local deterministic data and no live provider secrets.
   - Inventory the primary React routes and workflows built through H8: login, dashboard, seed universe, screener, security detail, in-depth review, watchlist, portfolio builder, rebalancing, and alerts.
   - Record any environment blockers separately from product blockers, including exact commands, errors, and fallback evidence if a runtime path cannot be exercised.

2. **End-to-end product walkthrough**
   - Authenticate as the relevant demo users and verify protected-route behavior, session state, navigation labels, and role-sensitive controls.
   - Walk the main research journey from seeding symbols to screener/search, security detail, in-depth review, watchlist, portfolio flows, rebalancing, dashboard, and alerts.
   - Confirm that seeded securities behave as shared reference data and that watchlists/portfolios remain user-owned.
   - Verify that fair value, margin of safety, recommendation, and score displays include MiFID II decision-support disclaimers and avoid directive investment language.

3. **H8 Seed & Shared Universe assessment**
   - Test CSV entry, uppercase normalization, duplicate removal, invalid ticker feedback, pre-submit preview, loading state, partial success, full success, full failure, and retry behavior.
   - Verify admin named-pack visibility and non-admin hiding behavior according to the current backend policy.
   - Inspect result rows for source badges, source category coverage, fallback explanations, freshness labels, failed-row messages, company profile context, and handoffs to Screener, Security Detail, and In-Depth Review.
   - Confirm copy makes the shared-universe scope clear: seeding creates or refreshes platform-wide securities and financial data, not personal watchlist or portfolio entries.

4. **Cross-surface UI, copy, and accessibility review**
   - Review visual hierarchy, spacing, density, table readability, form layout, badge clarity, button/action placement, focus states, loading states, empty states, error states, and mobile/desktop responsiveness.
   - Check for text overlap, clipped labels, cramped controls, unstable layouts, color-only meaning, missing accessible names, poor focus order, and insufficient contrast.
   - Compare navigation labels, page headings, route transitions, action naming, status language, and disclaimer placement for consistency across primary surfaces.
   - Evaluate whether each page feels like an operational product workflow rather than a marketing or static explanation page.

5. **Assessment report and scoped fixes**
   - Create an assessment report in this spec directory with findings grouped as blockers, polish fixes, accessibility issues, copy issues, and deferred improvements.
   - For each finding, include severity, affected surface, observed behavior, expected behavior, and recommended resolution.
   - Apply low-risk visual or copy fixes immediately when the change is clearly scoped, testable, and does not alter backend behavior or product policy.
   - Capture larger UX, product, backend, data, or compliance changes as deferred improvements or follow-up roadmap recommendations.

6. **Verification and merge readiness**
   - Run frontend linting, TypeScript checks, tests, and production build where supported.
   - Run backend compile/tests or targeted smoke checks needed to support the local demo path.
   - Re-run the affected browser walkthrough after any immediate fixes.
   - Run `git diff --check` and confirm no secrets, live provider dependencies, or unrelated refactors were introduced.
