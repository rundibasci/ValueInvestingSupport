# Validation — DL1: Portfolio Lifecycle Completion

## Functional Acceptance

- `DELETE /api/v1/portfolios/{id}` returns `204 No Content` for an authenticated owner.
- The deleted portfolio no longer appears in list, detail, analytics, simulation, rebalance, or dashboard-backed portfolio reads.
- Holdings, rebalance proposals/lines, and analytics snapshots belonging to the portfolio are deleted.
- Shared securities, market-data snapshots, valuations, scores, other users' data, and immutable research-decision audits remain unchanged.
- Unknown and cross-owner portfolio IDs both return `404` without revealing ownership.
- An unauthenticated request returns `401`.
- Repeating deletion after success returns `404`.
- The frontend requires a confirmation naming the portfolio before calling the API.
- Cancelling confirmation leaves all state unchanged.
- Successful deletion selects another available portfolio or displays the existing empty state when the last portfolio was deleted.
- Failed deletion displays an actionable error and preserves the selected portfolio view.

## Backend Automated Checks

- Controller test: owner request maps to service and returns `204` with an empty body.
- Controller test: service `404` propagates without an ownership-specific payload.
- Service test: authenticated user resolution and `findByIdAndUser` are used before deletion.
- Service test: unresolved portfolio never reaches `repository.delete`.
- Integration test: empty portfolio deletion succeeds.
- Integration test: holdings are removed by cascade.
- Integration test: rebalance proposal and line rows are removed by cascade.
- Integration test: portfolio analytics snapshots are removed by cascade.
- Integration test: unrelated portfolio and its children remain.
- Integration test: shared security/fundamental/quote/valuation rows remain.
- Integration test: immutable research audit evidence remains.
- Security test: unauthenticated request is `401`; authenticated non-owner receives `404`.

Suggested commands, adjusted to existing test class names:

```bash
cd backend
./mvnw -Dtest=PortfolioControllerTest,PortfolioServiceTest,PortfolioIT test
./mvnw test
./mvnw -DskipTests package
```

Use Java 21 as required by `specs/tech-stack.md`.

## Frontend Automated Checks

- API client test verifies DELETE method, URL, `204` handling, and error propagation.
- Portfolio page test verifies confirmation cancellation.
- Portfolio page test verifies pending/disabled behavior.
- Portfolio page test verifies selection of a remaining portfolio.
- Portfolio page test verifies the last-portfolio empty state.
- Portfolio page test verifies failure recovery without clearing visible data.
- Accessibility-oriented test verifies an understandable destructive label and named confirmation.

Suggested commands:

```bash
cd frontend
npm run typecheck
npm run build
```

Run the repository's frontend test command if one is defined when implementation begins.

## Manual Review

1. Sign in as an INVESTOR and create two portfolios.
2. Add at least one holding to the portfolio selected for deletion.
3. Generate or persist analytics and a rebalance proposal if the environment has eligible data.
4. Choose `Delete portfolio` and cancel; verify no network DELETE is sent and the portfolio remains.
5. Confirm deletion; verify the confirmation names the portfolio and explains the permanent scope.
6. Verify another portfolio is selected and the deleted portfolio is absent from the dashboard selector.
7. Delete the remaining empty portfolio and verify the established no-portfolio state.
8. Repeat the exercise with another user's portfolio ID and verify `404` with no ownership disclosure.
9. Verify retained research audit entries remain readable.
10. Confirm no direct PostgreSQL deletion is required for cleanup.

## Regression and Safety Checks

- Portfolio creation, detail, holding add/update/remove, analytics, simulation, rebalance creation/application, and dashboard selection continue to work.
- Watchlist, checklist, account, audit, security-detail, valuation, screener, and seed workflows are unaffected.
- No new migration is added unless a missing cascade is discovered during Task Group 1.
- No financial values, recommendations, or market-data records are recalculated by deletion.
- No secret, raw database error, or foreign ownership detail is exposed.
- `git diff --check` passes.

## Merge Criteria

- All functional acceptance statements pass.
- Targeted backend and frontend tests pass on Java 21/Node versions supported by the repository.
- Backend package and frontend production build pass.
- The PostgreSQL cascade integration test proves both deletion and preservation boundaries.
- Ownership behavior is tested as `404`, not `403`.
- The real-demo cleanup can be completed entirely through the product UI/API.
- Documentation no longer instructs operators to delete temporary portfolios directly from PostgreSQL.
- No unrelated behavior or files are changed.

## Implementation Results

- Existing database/JPA relationship review: PASS. Holdings, rebalance proposals/lines, and analytics snapshots already use portfolio-scoped cascade deletion; no Flyway migration was required.
- Research audit boundary review: PASS. `research_snapshot` is user/security scoped and has no portfolio foreign key.
- Backend focused unit tests (`PortfolioControllerTest`, `PortfolioServiceTest`): PASS, 21 tests on Java 21.
- PostgreSQL integration tests (`PortfolioIT` with the Maven `integration-test` profile): PASS, 6 tests on Java 21 and PostgreSQL 16.
- Cascade preservation test: PASS for holdings, rebalance proposal/lines, analytics snapshots, unrelated portfolios, and immutable research snapshots.
- Frontend TypeScript typecheck: PASS on Node 22.
- Frontend production build: PASS on Node 22; the existing bundle-size warning remains informational.
- Backend full unit suite: 390 tests executed; all DL1 tests pass. One pre-existing unrelated failure remains in `UniverseSelectionServiceTest.preview_fallsBackToSeededSecuritiesWhenFmpStockListIsUnavailable`.
- Backend Java 21 package: PASS.
- `git diff --check`: PASS.
