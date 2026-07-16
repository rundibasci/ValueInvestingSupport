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

- [x] Migration applies from the current schema and validates cleanly on PostgreSQL. *(Verified 2026-07-17: Flyway applied all 25 migrations, including V25 portfolio_analysis_run, cleanly against Testcontainers PostgreSQL in `PortfolioIT`, `ScreenerIT`, and `SecurityDetailIT`.)*
- [ ] Repository tests cover uniqueness, indexes/queries, atomic claim, terminal transitions, retry correlation, and owner scoping. *(Tests exist and pass as part of the green 410-test suite; assertion-by-assertion content was not individually audited against this line in this pass.)*
- [ ] Coordinator tests cover bounded concurrency, active joining, recovery, independent symbol failure, and all terminal states. *(Same caveat as above.)*
- [ ] Orchestrator tests prove reuse of shared ingestion/calculation services and complete/unavailable readiness summaries. *(Same caveat as above.)*
- [ ] Integration tests cover the complete import-to-analytics chain, mixed provider coverage, cash, duplicate lots, missing history, partial failure, retry, and idempotent rerun. *(Same caveat as above.)*
- [ ] Security tests prove a second user cannot start, discover, read, or retry another user's run. *(Same caveat as above.)*
- [x] Existing FI1/FI2 import, seed, Security Review, valuation, scoring, portfolio, and analytics tests remain green. *(Verified 2026-07-17: full backend suite 410/410 pass; integration profile 53/53 pass with a real FMP key configured.)*

Suggested commands (adjust only to repository-supported test selectors):

```bash
cd backend
./mvnw test

cd ../frontend
npm test -- --run
npm run build
```

## Automated Frontend Checks

- [x] TypeScript compiles in strict mode and the production build succeeds. *(Verified 2026-07-17: `npm run typecheck` and `npm run build` both pass with Node v24.18.0.)*
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

## Re-Verification — 2026-07-17

The 2026-07-16 evidence above was blocked purely by host constraints: only a Java 26 JDK was installed (breaking Mockito's inline mock maker, which requires Java 21) and `npm`/Docker were unavailable. Those constraints were resolved for this pass: a local Temurin 21 JDK was installed project-side (`JAVA_HOME` override only, no system Java changed), Node v24.18.0/npm 11.16.0 were made available via the existing `nvm` installation, and Docker Desktop was started.

- `JAVA_HOME=<local-temurin-21> ./mvnw test` (full suite, no filter) — **410/410 tests PASS**, including `PortfolioAnalysisControllerTest`, `PortfolioAnalyticsServiceTest`, `PortfolioRebalanceServiceTest`, and the rest of the `portfolio`/`portfolio.analysis` packages.
- A real FMP API key was subsequently configured locally (`.env` and `backend/src/test/resources/application-fmpkey.yml`, both gitignored). `JAVA_HOME=<local-temurin-21> ./mvnw test -Pintegration-test` (Testcontainers PostgreSQL, Docker Redis via `docker-compose.demo.yml`) — **53/53 tests PASS**, including `PortfolioIT`, `PipelineDemoIT`, `FmpMarketDataClientLiveIT`, and `ValuationDemoIT` against the real FMP API.
- `cd frontend && npm run typecheck` — **PASS** (strict `tsc -b`, no errors).
- `cd frontend && npm run build` — **PASS**, production bundle built.
- `git diff --check` — PASS.

Four genuine, pre-existing bugs were found and fixed while getting the suite green (all unrelated to FI3 itself — see the FI2 `validation-report.md` Re-Verification section for full detail, since all were caught while establishing the shared full-suite baseline FI3 also depends on):
1. Group SC's `UniverseSelectionServiceTest` had an under-stubbed test case.
2. Group LS's H2 Flyway migration `V11__watchlist_rationale.sql` used Postgres-only multi-column `ALTER TABLE` syntax.
3. `FmpMarketDataClientLiveIT` didn't tolerate the documented empty-list plan-restriction outcome from `listSymbols`.
4. **15 JPA entities referencing `security_id`** (across Groups SR, MA, and the original core schema) were missing the Hibernate `@OnDelete(action = OnDeleteAction.CASCADE)` annotation despite every one of their Flyway migrations declaring `ON DELETE CASCADE` — invisible in production (Postgres's own DDL already cascades) but broke `PipelineDemoIT`/`ValuationDemoIT`'s H2 `create-drop` teardown the moment the pipeline actually persisted Piotroski/Altman/moat/dividend/etc. data for a real symbol, which only happened once a working FMP key was in place.

**Still not done, and out of scope for an automated re-verification pass:**
- The manual provider-backed CSV analysis walkthrough (real FMP/Yahoo credentials, a running application, and the supplied portfolio file) has not been performed.
- No frontend test runner exists in this repository (confirmed absent, not merely unrun) — FI2's own spec called for adding one; FI3's frontend API/component/interaction test matrix in this document has not been executed because there is no test runner to execute it with. This is a real gap, not a re-verification blocker specific to FI3.
