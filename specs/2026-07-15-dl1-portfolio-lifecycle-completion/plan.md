# Plan — DL1: Portfolio Lifecycle Completion

## Task Group 1: Confirm Cascade and Audit Boundaries

1.1 Inventory every foreign key and JPA relationship that references `portfolio`, including holdings, rebalance proposals/lines, and analytics snapshots.

1.2 Verify that each portfolio-scoped child is covered by an existing JPA cascade or PostgreSQL `ON DELETE CASCADE`; add a Flyway migration only if verification reveals a missing database cascade.

1.3 Confirm that research-decision audit records do not require portfolio deletion and remain append-only.

1.4 Record the verified deletion graph in test names or test setup so future schema changes cannot silently leave orphan data.

## Task Group 2: Backend Deletion Service and API

2.1 Add a transactional `deletePortfolio(Authentication auth, UUID id)` operation to `PortfolioService`.

2.2 Reuse `resolveUser(auth)` and `resolvePortfolio(id, user)` so missing and cross-owner portfolios both produce the established `404` response.

2.3 Delete the resolved portfolio through `PortfolioRepository`; do not issue manual deletes for shared research data or immutable audit records.

2.4 Add `DELETE /api/v1/portfolios/{id}` to `PortfolioController` with `204 No Content`.

2.5 Preserve the existing global security rules so authenticated `INVESTOR`, `ADVISOR`, and `ADMIN` roles can delete only their own portfolios and unauthenticated requests receive `401`.

## Task Group 3: Backend Verification

3.1 Extend controller tests for successful `204`, malformed UUID handling where applicable, and service-level `404` propagation.

3.2 Add service tests proving that the repository receives only an ownership-resolved portfolio and that a missing/cross-owner portfolio is not deleted.

3.3 Add a PostgreSQL integration test that creates a portfolio with holdings, a rebalance proposal and lines, and an analytics snapshot, deletes the portfolio through the API/service, and verifies all portfolio-scoped rows are gone.

3.4 Verify that unrelated portfolios, shared securities/market data, and immutable research audit rows remain present.

3.5 Cover empty-portfolio deletion and repeated deletion (`404` after the first successful request).

## Task Group 4: Frontend API and Mutation

4.1 Add `portfolioApi.remove(id): Promise<void>` using `DELETE /api/v1/portfolios/{id}` and the existing API error conventions.

4.2 Add a TanStack Query mutation to `PortfolioPage` and keep pending/error state scoped to the selected portfolio.

4.3 On success, remove/invalidate cached detail, analytics, simulation, and rebalance data for the deleted ID and refresh the portfolio list and dashboard-related portfolio queries.

4.4 Resolve post-delete selection deterministically: first remaining portfolio when present, otherwise no selected ID and the existing empty state.

## Task Group 5: Destructive-Action UX

5.1 Add a clearly destructive `Delete portfolio` action near portfolio-level controls, not near individual holding actions.

5.2 Require explicit confirmation containing the portfolio name and explaining that holdings, analytics snapshots, and saved rebalance proposals are removed permanently.

5.3 Disable duplicate submission while deletion is pending and keep the page stable until the server confirms success.

5.4 Show a success acknowledgement and an actionable failure message. A failed request must leave the current portfolio and cached content visible.

5.5 Ensure keyboard access, visible focus, appropriate dialog/confirmation semantics, and readable mobile layout.

## Task Group 6: Frontend Tests

6.1 Test cancellation: declining confirmation produces no API request.

6.2 Test success with multiple portfolios: the selected portfolio is deleted, caches are invalidated, and another portfolio becomes active.

6.3 Test deletion of the last portfolio: selection clears and the existing empty state is rendered.

6.4 Test pending state and duplicate-click prevention.

6.5 Test API failure: error is visible and the portfolio remains selected.

6.6 Test accessible action and confirmation labeling with the portfolio name.

## Task Group 7: Documentation and Regression Check

7.1 Update relevant API/product documentation to list portfolio deletion and its ownership/cascade semantics.

7.2 Update the real-demo walkthrough limitation or add follow-up evidence showing that direct database cleanup is no longer required.

7.3 Run targeted backend/frontend tests, broader builds, and `git diff --check`.

7.4 Perform a reversible real-demo smoke test: create portfolio, add holding, delete portfolio through the UI, and verify it disappears from portfolio and dashboard surfaces.

