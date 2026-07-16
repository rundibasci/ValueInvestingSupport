# FI3 — Validation and Merge Criteria

## Functional Acceptance

- [ ] A committed import offers **Seed and analyze portfolio** and returns one owner-scoped `analysisRunId`.
- [ ] The supplied CSV creates outcomes for every distinct resolved coded position; EUR and USD cash remain cash and never enter security ingestion or valuation.
- [ ] Multiple lots of one symbol are analyzed once but all lots contribute correctly to portfolio value.
- [ ] Each successful security exposes the full installed Security Review packet and calculation set, not a broker-only summary.
- [ ] FMP remains primary, Yahoo fallback is visible by data category, and provider-plan gaps are explained.
- [ ] Run and outcome states progress through the documented lifecycle with accurate counts and timestamps.
- [ ] One failed symbol does not block remaining symbols; portfolio aggregation waits until every symbol is terminal and then identifies partial coverage.
- [ ] Broker price/EUR value and refreshed platform price/value remain side by side with source, time, and variance.
- [ ] Rerunning refreshes stale inputs without duplicating portfolios, holdings, securities, snapshots, valuations, scores, or analytics records.
- [ ] Retry failed/partial submits only eligible outcomes and links the retry to its predecessor.
- [ ] Reloading the page resumes the active/latest run; polling stops at terminal state.
- [ ] Every fair value, score, risk, benchmark, and rebalance output shows assumptions/data dates/limitations and the decision-support disclaimer.

## Data Integrity and Explainability

- [ ] Run inputs are immutable snapshots of the committed import, positions, broker evidence, and cash at submission time.
- [ ] Equivalent active submissions join a single run using a deterministic owner/portfolio/import/symbol/version fingerprint.
- [ ] Holding edits after submission do not mutate historical results and visibly supersede the old run.
- [ ] Each metric records source category/date, freshness, calculation version, assumptions, eligibility, and guardrail where applicable.
- [ ] Missing or ineligible metrics are null/unavailable with a supported reason; no test or UI path converts missing data to zero.
- [ ] Cash affects total value, allocation, and currency exposure only.
- [ ] Portfolio weighted metrics publish numerator/denominator coverage and do not imply full coverage from partial holdings.
- [ ] Shared universe data remains shared while run, import, cash, reconciliation, and analytics data remain user-owned.

## Automated Backend Checks

- [ ] Migration applies from the current schema and validates cleanly on PostgreSQL.
- [ ] Repository tests cover uniqueness, indexes/queries, atomic claim, terminal transitions, retry correlation, and owner scoping.
- [ ] Coordinator tests cover bounded concurrency, active joining, recovery, independent symbol failure, and all terminal states.
- [ ] Orchestrator tests prove reuse of shared ingestion/calculation services and complete/unavailable readiness summaries.
- [ ] Integration tests cover the complete import-to-analytics chain, mixed provider coverage, cash, duplicate lots, missing history, partial failure, retry, and idempotent rerun.
- [ ] Security tests prove a second user cannot start, discover, read, or retry another user's run.
- [ ] Existing FI1/FI2 import, seed, Security Review, valuation, scoring, portfolio, and analytics tests remain green.

Suggested commands (adjust only to repository-supported test selectors):

```bash
cd backend
./mvnw test

cd ../frontend
npm test -- --run
npm run build
```

## Automated Frontend Checks

- [ ] TypeScript compiles in strict mode and the production build succeeds.
- [ ] API-client tests cover accepted/joined responses, status/outcome parsing, latest run, and retry.
- [ ] Component tests cover queued/seeding/calculating/complete/partial/failed rendering and accurate counts.
- [ ] Interaction tests cover start, duplicate-click prevention, polling/backoff, reload resume, retry, review navigation, and superseded results.
- [ ] Reconciliation, availability, freshness, coverage, cash, and disclaimer labels are asserted.

## Manual Review

1. Import and commit the supplied `Portfolio.csv` as a normal investor.
2. Start analysis and observe each non-cash symbol progress while EUR/USD cash creates no security outcome.
3. Open successful holdings and confirm their Security Review pages show full installed valuation, score, risk/quality, source, and freshness content.
4. Force or stub one provider failure and one model-ineligible metric; verify other symbols finish and both gaps have plain-language reasons.
5. Confirm portfolio results appear only after all symbol outcomes are terminal and display partial coverage when appropriate.
6. Compare broker and refreshed prices/values, sources, timestamps, and variances; confirm neither source overwrites the other.
7. Retry failed/partial work and rerun the portfolio; inspect database counts to confirm analytical and holding records are not duplicated.
8. Sign in as a second user and verify the first user's run IDs behave as not found.
9. Refresh during an active run and confirm the UI resumes it; edit the portfolio afterward and confirm the completed run is marked superseded.
10. Review desktop and mobile layouts, keyboard focus, unavailable states, and disclaimer placement.

## Operational and Safety Checks

- [ ] Provider calls respect cache-first behavior, timeouts, retry/circuit-breaker policy, quotas, and configured concurrency.
- [ ] Interrupted queued/running work is safely recovered or terminally explained without duplicate processing.
- [ ] Logs and metrics correlate `analysisRunId` and symbol while excluding credentials, tokens, and raw sensitive payloads.
- [ ] Large allowed imports remain responsive, outcome pagination is bounded, and polling does not create a request storm.
- [ ] No automated trade, personalized advice, or unsupported recommendation is introduced.

## Merge Gate

- [ ] All functional acceptance and data-integrity items pass.
- [ ] Targeted and full backend suites pass.
- [ ] Frontend tests, strict typecheck/build, and accessibility checks pass.
- [ ] Flyway validation and clean-schema integration pass.
- [ ] `git diff --check` reports no errors.
- [ ] No secret, generated build artifact, raw provider payload, or unrelated change is included.
- [ ] Any environment-dependent test not run is documented with owner and follow-up; no unresolved high-severity defect remains.
- [ ] `validation.md` is updated with actual commands, results, and evidence before merge.

## Implementation Evidence — 2026-07-16

- `cd backend && ./mvnw -q -DskipTests compile` — PASS.
- `cd backend && ./mvnw -q test-compile` — PASS, including the FI3 controller contract test.
- `cd backend && ./mvnw -q -Dtest=PortfolioAnalysisControllerTest test` — BLOCKED by the same Java 26/Mockito agent initialization failure before the test method runs; 1 test discovered, 0 assertion failures, 1 initialization error.
- `cd backend && ./mvnw -q test` — BLOCKED BY LOCAL TEST RUNTIME: Java 26 prevents the configured Mockito inline mock maker from attaching. The suite discovered 394 tests; 301 errored during Mockito initialization with no assertion failures. This is environment/toolchain failure, not a recorded FI3 assertion failure.
- `cd frontend && npm run typecheck` — NOT RUN: `npm` is not installed in the current shell.
- `git diff --check` — PASS.
- Manual provider-backed CSV analysis remains required before merge because it needs PostgreSQL, provider credentials/coverage, and the supplied portfolio in a running application.
