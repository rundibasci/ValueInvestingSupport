# H4B - Review Page Portfolio-Add Integration Validation

## Functional acceptance

- Authenticated users can open `/securities/:symbol/review` for a seeded symbol and see a functional add-to-portfolio action.
- The old disabled "coming soon" portfolio action is removed or replaced with the functional H4B control.
- The user can select only portfolios returned by the authenticated portfolio-list API.
- Adding the reviewed symbol to a selected portfolio uses the existing portfolio holdings API contract.
- A successful add shows clear confirmation with the symbol and portfolio name.
- The affected portfolio data is refreshed or invalidated so the added holding appears in the portfolio UI without a full browser refresh.
- Users with no portfolios see a useful empty state and a path to create one through the existing portfolio experience.
- Duplicate holdings are handled explicitly and do not create silent duplicated rows.
- Authorization, ownership, validation, not-found, duplicate, network, and expired-session errors are clear and recoverable.
- Review-page research data remains visible if the portfolio picker or mutation fails.
- Signed-out users follow the existing protected-route login flow and can return to the review page after authentication.
- Existing H4A content remains intact: source coverage, freshness, valuation evidence, financial-health evidence, historical graphs, custom DCF controls, watchlist action, and data-quality labels.
- MiFID II decision-support disclaimer appears wherever fair value, margin of safety, recommendation, score, or valuation-derived portfolio context is shown.
- The UI never implies a trade was placed, guarantees a return, or converts the action into buy/sell advice.

## Automated checks

- Frontend linting, TypeScript type checking, unit/component tests, and production build pass.
- Focused frontend tests cover:
  - authenticated portfolio-list loading on the review page;
  - no-portfolio empty state;
  - portfolio picker rendering and selection;
  - successful add-holding mutation;
  - cache invalidation or refetch of affected portfolio queries;
  - duplicate-holding API response handling;
  - validation, unauthorized, forbidden, not-found, expired-session, and network-error states;
  - preservation of already loaded review-page data during portfolio errors;
  - no mutation call when no portfolio is selected or required holding fields are invalid;
  - MiFID II disclaimer rendering in valuation/portfolio-adjacent contexts.
- Regression tests verify the H4A review packet still renders the required single-stock research evidence: DCF, FCF, Graham number, margin of safety, earnings, debt, dividend sustainability, dividend yield, quick ratio when available, source coverage, freshness, and unavailable-data labels.
- A deterministic browser or integration test verifies the H4B journey without live FMP/Yahoo calls or secrets:
  1. authenticate as a non-admin investor;
  2. ensure the investor owns at least one test portfolio;
  3. open `/securities/AAPL/review` or another seeded fixture symbol;
  4. select the owned portfolio;
  5. add the symbol;
  6. navigate to the portfolio view and verify the holding appears;
  7. attempt the same add again and verify duplicate handling.

## Manual review

- Check desktop and narrow layouts for stable action placement, readable picker contents, no text overlap, and no disruption to the long-form review reading flow.
- Verify keyboard-only operation, focus order, escape/close behavior if a modal or popover is used, visible focus states, and screen-reader-readable success/error statuses.
- Verify color contrast for confirmation, warning, duplicate, and error states.
- Verify financial wording remains descriptive, non-directive, and does not imply trade execution.
- Confirm portfolio ownership remains user-owned while the reviewed security remains part of the shared seeded universe.
- Confirm no provider secrets, JWT refresh tokens, raw credentials, stack traces, internal diagnostics, or sensitive user data appear in source, fixtures, logs, URLs, local storage, or rendered debug output.

## Merge criteria

- All automated checks pass, including production build and deterministic authenticated review-to-portfolio journey.
- `/securities/:symbol/review` lets an authenticated user add the reviewed symbol to an owned portfolio through existing portfolio APIs.
- Existing H4A review-page behavior, H5 portfolio behavior, auth handling, query-client conventions, and shared UI components have no regressions.
- Duplicate, empty, unauthorized, forbidden, expired-session, validation, and network states are handled cleanly.
- The implementation respects portfolio ownership, shared research-universe boundaries, and the decision-support/MiFID II guardrails.
- The resolved feature-spec decision in `requirements.md` is preserved: portfolio creation remains on the existing Portfolio page, not inline on the review page.
