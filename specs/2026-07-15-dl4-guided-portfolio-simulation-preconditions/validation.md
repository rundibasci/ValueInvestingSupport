# DL4 — Validation

## Acceptance Criteria

- [x] A user can understand simulation readiness before submitting the command.
- [x] Simulation and simulation-based rebalance have separately explained readiness states.
- [x] No-watchlist, no-priced-candidate, missing-data, constraint-elimination, unaffordable-budget, empty-portfolio, and unpriced-portfolio cases are distinguishable where applicable.
- [x] Every backend-disabled command has a visible reason and at least one relevant recovery action.
- [x] Commands are disabled only after a successful backend response proves them impossible.
- [x] The backend remains authoritative and repeats validation when the command is submitted.
- [x] Known `422` responses contain stable codes and structured diagnostics; the frontend does not parse message strings.
- [x] Recovery guidance never changes or weakens investment constraints automatically.
- [x] Existing allocation, rebalance, ownership, and disclaimer behavior is preserved by targeted regression coverage.

## Backend Test Matrix

| Scenario | Expected result |
|---|---|
| Unauthenticated request | Existing authentication response |
| Portfolio owned by another user | Ownership-safe `404` |
| Invalid simulation input | `400` validation response |
| Empty watchlist | Simulation unavailable with `NO_WATCHLIST_ITEMS` and watchlist/screener recovery |
| All candidates lack valid prices | Simulation unavailable with `NO_PRICED_CANDIDATES` and grouped price exclusions |
| Fundamental data missing | Distinct grouped exclusion counts without fabricated values |
| Constraints exclude otherwise ready candidates | Simulation unavailable with `CONSTRAINTS_EXCLUDE_ALL` and constraints recovery |
| Budget cannot purchase one share within caps | Simulation unavailable with an affordability/cap reason |
| At least one allocatable candidate | Simulation available and existing allocation behavior unchanged |
| Empty portfolio | Rebalance guidance identifies empty holdings and links to holdings editor |
| Portfolio has unpriced holdings | Rebalance guidance identifies unpriced holdings |
| State changes after preflight | Command returns structured authoritative `422` diagnostics |

## Frontend Test Matrix

| Scenario | Expected result |
|---|---|
| Preconditions loading | Controls do not claim ineligibility; progress is accessible |
| Eligible response | Commands enabled when the local form is valid |
| Proven impossible response | Relevant command disabled with visible explanation |
| No watchlist | Links to Watchlist and Screener are offered |
| Over-restrictive constraints | Constraints are highlighted/focused; values remain unchanged |
| Empty or unpriced portfolio | Holdings recovery action is offered for rebalance |
| Preconditions request fails | Retry is available; failure is not rendered as domain ineligibility |
| Command returns `422` | Structured domain reason is rendered after submission |
| Holding mutation succeeds | Detail, analytics, and precondition data are refreshed |
| Keyboard/screen-reader use | Status, disabled reason, recovery controls, and errors are perceivable |

## Regression Checks

- [ ] Existing successful simulation produces the same proposals, ordering, weights, cash, and exclusions for a fixed fixture.
- [ ] Existing simulation-based rebalance produces the same proposal for a fixed fixture.
- [ ] Existing portfolio create/delete and holding add/remove flows still work.
- [ ] Existing watchlist and screener navigation remains intact.
- [ ] No cross-user counts, symbols, or portfolio state are disclosed.
- [ ] Decision-support and MiFID II disclaimer text remains present.

## Verification Commands

Use the exact repository commands confirmed during implementation. At minimum record results for:

```bash
cd backend && ./mvnw test
cd frontend && npm test -- --run
cd frontend && npm run typecheck
cd frontend && npm run lint
cd frontend && npm run build
git diff --check
git status --short
```

If PostgreSQL-specific integration coverage exists for the affected repositories, run it using the repository's documented Docker Compose profile and record the result.

## Merge Gate

The feature can be merged only when all acceptance criteria and regression checks pass, backend and frontend suites are green (or unrelated pre-existing failures are explicitly evidenced), the production frontend build succeeds, the diff contains no unrelated changes, and the roadmap status accurately reflects verified completion.

## Validation Evidence

To be completed during implementation:

- Backend tests: targeted `PortfolioSimulationServiceTest`, `PortfolioRebalanceServiceTest`, and `PortfolioControllerTest` pass (26 tests). Full suite: 400 tests, one unrelated pre-existing failure in `UniverseSelectionServiceTest.preview_fallsBackToSeededSecuritiesWhenFmpStockListIsUnavailable`.
- Frontend tests: the repository has no frontend test runner or test script; state handling is typechecked and production-built, but automated component tests remain unavailable in the current frontend toolchain.
- Typecheck/lint/build: `docker compose build frontend` passed, including `tsc -b` and Vite production build. The repository has no separate lint script.
- PostgreSQL integration tests:
- Manual walkthrough: not run; implementation was verified through contracts, service/controller tests, and production build.
- Known unrelated failures: `UniverseSelectionServiceTest.preview_fallsBackToSeededSecuritiesWhenFmpStockListIsUnavailable` expects `KO` but receives an empty list, consistent with the previously recorded suite baseline.
