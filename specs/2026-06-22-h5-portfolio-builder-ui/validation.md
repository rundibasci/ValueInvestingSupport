# H5 — Portfolio Builder UI Validation

## Functional acceptance

- An authenticated user can navigate to the portfolio area, see their portfolios, create a portfolio, view its detail, and manage holdings.
- The builder accepts budget, risk profile, yield target, and all supported allocation constraints.
- A simulation produces a transparent allocation proposal with exclusions/reasons where applicable.
- Users can edit proposed weights; the UI immediately reports invalid total allocation or sector, stock, and country concentration limits and does not permit saving until valid.
- Sector allocation is displayed in a donut chart with a readable data alternative.
- A valid proposal saves successfully and appears in the portfolio list/detail view.
- A user can request and inspect a rebalance proposal showing current weight, target weight, difference, and recommended action.
- Loading, validation, empty, API-error, unauthorized, and not-found states are understandable and recoverable.
- Portfolio views that surface valuation/recommendation-derived data show the MiFID II decision-support disclaimer.

## Automated checks

- Frontend linting, TypeScript type checking, unit/component tests, and production build all pass.
- An end-to-end browser test runs against the application and verifies this complete journey:
  1. authenticate with a test user;
  2. open the portfolio builder;
  3. supply budget, risk/yield inputs, and allocation constraints;
  4. run a simulation and verify the proposed allocations and constraint feedback;
  5. save a valid portfolio and verify its detail/holdings view;
  6. request and verify a rebalance proposal.
- The test uses deterministic backend fixtures or seeded test data and must not require live FMP/Yahoo calls or secrets.

## Manual review

- Check desktop and narrow-screen layouts, keyboard form operation, focus/error announcements, chart labels, and color-independent status indicators.
- Confirm no portfolio or allocation data is presented as investment advice and no secret is added to the frontend.

## Merge criteria

- All automated checks pass.
- The end-to-end browser flow passes reliably from a clean local test environment.
- No regression is introduced in existing H1–H4 routes, authentication, or API-client behavior.
- Scope remains limited to H5 and all review feedback is resolved.
