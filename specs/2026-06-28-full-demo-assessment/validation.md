# HD1 - Full Demo UI Assessment Validation

## Functional acceptance

- The localstack/full-demo environment and React frontend are run against deterministic local data, or any runtime blocker is documented with exact commands, errors, and fallback evidence.
- The assessment covers the complete authenticated journey: login, dashboard, seed universe, screener, security detail, in-depth review, watchlist, portfolio builder, rebalancing, and alerts.
- H8 Seed & Shared Universe UI is specifically assessed for CSV preview, duplicate removal, invalid ticker feedback, submission states, role-sensitive named-pack visibility, source badges, fallback messaging, freshness labels, partial-success rows, failed-row errors, and research handoffs.
- The assessment verifies that seeding creates shared reference data and does not imply personal watchlist or portfolio ownership.
- All fair value, margin of safety, recommendation, and score displays reviewed during the walkthrough include decision-support framing and MiFID II disclaimers.
- Primary React surfaces are reviewed for visual hierarchy, spacing, density, table readability, form ergonomics, badges, buttons, focus states, loading states, empty states, error states, and mobile/desktop responsiveness.
- Any low-risk visual or copy fixes are implemented only when clearly scoped and do not change backend behavior.
- Larger changes are captured as deferred improvements or follow-up roadmap recommendations.

## Assessment report

- The report is stored under `specs/2026-06-28-full-demo-assessment/`.
- Findings are grouped under these headings:
  - Blockers
  - Polish fixes
  - Accessibility issues
  - Copy issues
  - Deferred improvements
- Each finding includes:
  - Severity;
  - affected route or surface;
  - observed behavior;
  - expected behavior;
  - recommended resolution.
- The report distinguishes environment/runtime blockers from product blockers.
- The report calls out any areas not exercised and explains why.

## Automated checks

- Frontend linting passes where configured.
- Frontend TypeScript checking passes where configured.
- Frontend unit/component tests pass where configured.
- Frontend production build passes.
- Backend compile or targeted tests needed for the local demo path pass where supported.
- `git diff --check` passes.

## Manual review

- Desktop and narrow/mobile viewport passes confirm no text overlap, clipped controls, incoherent wrapping, or layout instability on the primary workflows.
- Keyboard-only navigation reaches primary controls in a logical order and shows visible focus states.
- Interactive controls have accessible names and do not rely on color alone for state.
- Dense tables remain readable and preserve important company context: symbol, company name, sector, exchange, country where available, and profile/description excerpt where available.
- Empty, loading, partial-data, stale-data, unavailable, unauthorized, expired-session, and error states use clear non-technical language.
- Handoffs between Seed Universe, Screener, Security Detail, In-Depth Review, Watchlist, and Portfolio flows are visible and consistent.
- No provider secrets, JWT refresh tokens, credentials, raw provider payloads that violate display constraints, stack traces, or sensitive user data appear in source, fixtures, rendered UI, local storage, logs, screenshots, or the assessment report.

## Merge criteria

- The spec decisions in `requirements.md` are resolved.
- The assessment report is complete and grouped by the required categories.
- All feasible low-risk fixes identified during HD1 are implemented and rechecked.
- Any remaining blockers or larger changes are documented with follow-up recommendations.
- Automated checks and `git diff --check` pass, or failures are documented with concrete reasons and owner/phase recommendation.
- The branch contains only HD1 spec, report, and scoped low-risk assessment/fix work.
