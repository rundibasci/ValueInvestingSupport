# TA6 — Validation

## Verification Commands (as actually run this session)

1. `cd backend && ./mvnw -o test-compile` — clean compile after the `classifyTrend` change.
2. `cd backend && ./mvnw -o test -Dtest='it.mazzoni.vis.thesis.**'` — **34/34 passed** (33 permanent `it.mazzoni.vis.thesis` tests, including the rewritten `classifyTrend_*` fixtures and the new `classifyTrend_readsNewestFirst_notOldestFirst`, plus a temporary live-verification spike deleted before merge — see Manual Verification).
3. `cd backend && ./mvnw -o test` — **full backend suite: 554/554 passed, 0 failures, 0 errors** (553 pre-existing + 1 new permanent test), zero regressions outside `ThesisInputBuilder`/`ThesisInputBuilderTest`.
4. `cd backend && ./mvnw -o test -Dtest='it.mazzoni.vis.moat.**'` — **13/13 passed**, confirming `MoatAssessmentService`'s own (unrelated, untouched) `classifyTrend` method and its tests are unaffected.
5. Real FMP `/income-statement` fetches (`curl`, real `FMP_API_KEY` from this repo's `.env`) for INTC, BA, META, KO, XOM — confirmed newest-first ordering for all five, and identified INTC's 7-year window as genuinely direction-sensitive (see Manual Verification).
6. A temporary spike test (`Ta6LiveVerificationSpike`, created and deleted within this session, never committed) called the real `ThesisInputBuilder.classifyTrend` method directly with INTC's real fetched revenue history — run once against the pre-fix code (via `git stash` of only `ThesisInputBuilder.java`) and once against the fix, both confirmed by actually executing the code.

## Acceptance Checks — Result

- [x] `classifyTrend` treats `history.get(0)` as the most recent data point — confirmed both by the new permanent unit test (`classifyTrend_readsNewestFirst_notOldestFirst`, `series("70","100")` → `STRONGLY_DECLINING`, which disagrees with the pre-fix reading of `STRONGLY_GROWING`) and by the live INTC spike (see below).
- [x] Every rewritten `classifyTrend_*` fixture passes with newest-first input ordering and an accurate ordering comment — `stronglyGrowing`/`growing`/`stable`/`declining`/`stronglyDeclining`/`volatile`, all green.
- [x] `classifyTrend`'s Javadoc states the newest-first convention explicitly, cross-referencing `deriveNetDebtToEbitda` and this phase's own requirements doc.
- [x] Full backend suite passes with zero regressions outside `ThesisInputBuilder`/`ThesisInputBuilderTest` — 554/554.
- [x] `MoatAssessmentService`'s own `classifyTrend` and its tests confirmed untouched and still green — 13/13, `it.mazzoni.vis.moat.**` run in isolation.
- [x] Live verification demonstrates the corrected direction against real, multi-year FMP data — INTC's real 2019-2025 revenue history: true latest period (2024→2025) is `-0.47%` (`STABLE`); the pre-fix implementation reads `-7.58%` (`DECLINING`) instead, a different `Trend` enum value, not just a different percentage. Confirmed by actually running both code paths against the same real data (git-stash toggle), not by hand-computation alone.
- [x] `git diff --stat main` confirmed to touch only `backend/src/main/java/it/mazzoni/vis/thesis/ThesisInputBuilder.java`, `backend/src/test/java/it/mazzoni/vis/thesis/ThesisInputBuilderTest.java`, `specs/roadmap.md`, and this spec directory.

## Manual Verification — What Was Actually Done

**Docker was not available in this session** (`docker info` failed) — unlike RM2/RM3/RM4's sessions, which had it. Rather than skip live verification or fall back to reasoning-only confirmation, verification was adapted to what this bug actually needs: `classifyTrend` is a pure function of a `List<BigDecimal>` history — it needs no database, cache, or running application to verify against real data, unlike RM4's `netDebtToEbitda` fix, which needed the full persisted-entity chain. This is a deliberate, disclosed deviation from `plan.md` Group 3's originally-envisioned Docker-based seed-and-generate flow, not a skipped step:

1. **[live]** Fetched real `/income-statement` annual revenue history (`period=annual&limit=7`) directly from FMP (`https://financialmodelingprep.com/stable`, real `FMP_API_KEY` from this repo's `.env`) for five real, well-known tickers: `INTC`, `BA`, `META`, `KO`, `XOM`. Confirmed all five newest-first (dates descending from index 0), consistent with `FmpAdapter`/`DemoAnalysisService`/RM4's own confirmed convention.
2. **[live]** Hand-scanned each ticker's window for a genuine direction disagreement between the true latest period-over-period change and what the pre-fix bug would read as "latest" (the oldest comparable period in the window). `BA` turned out to coincidentally agree in direction both ways (both `STRONGLY_GROWING`) despite very different magnitudes — not usable as a demonstration. `INTC` disagreed cleanly: true latest (2024→2025) = `-0.467%` (`STABLE`); the pre-fix reading (2020→2019, the oldest comparable pair in the 7-year window) = `-7.58%` (`DECLINING`).
3. **[live, code executed]** Wrote a temporary JUnit test (`Ta6LiveVerificationSpike`, `it.mazzoni.vis.thesis` package, never committed) hardcoding INTC's real fetched revenue values (with each period's real date recorded in a comment) and calling the real `ThesisInputBuilder.classifyTrend` method directly.
   - Ran it against the fix: **passed**, returned `Trend.STABLE`, `0` warnings — matching the hand-computed true latest-period classification.
   - `git stash push -- backend/src/main/java/it/mazzoni/vis/thesis/ThesisInputBuilder.java` to isolate just the production-code change (leaving the new test file and the updated `ThesisInputBuilderTest.java` fixtures in place), re-ran the same spike against the now-reverted (pre-fix) `ThesisInputBuilder`: **failed** — `expected: STABLE but was: DECLINING`, confirming the bug's real-world impact and the fix's correctness by actually executing both code paths, not by arithmetic alone.
   - `git stash pop` to restore the fix; re-ran the full `it.mazzoni.vis.thesis.**` suite (34/34, including the spike) to confirm the fix was correctly restored.
   - Deleted `Ta6LiveVerificationSpike.java` (it was never intended as a permanent test — its scenario's *mechanism* is already covered by the permanent `classifyTrend_readsNewestFirst_notOldestFirst` synthetic test; its purpose was solely to prove the fix against real fetched data before merge) and re-ran the full backend suite (554/554) to confirm a clean tree.
4. No standing infrastructure, credentials, or generated data were left behind — no Docker containers were started this session, and the only external call made was the read-only FMP fetch using the repo's own already-configured API key.

**Not performed this session:** the originally-planned full Docker seed → live Vertex AI thesis generation → `psql`/`input_snapshot` inspection flow — genuinely not needed for this specific fix (see rationale above), and explicitly disclosed here rather than silently substituted without comment.

## Corrections Made During Implementation (honest account)

1. **Docker was unavailable this session**, unlike the sessions that produced RM2/RM3/RM4's live verifications. Plan.md Group 3 anticipated a full seed-and-generate Docker flow; this was adapted to a targeted, code-executed verification against real fetched FMP data instead (Manual Verification above), which fully answers this phase's specific question (does `classifyTrend` read the correct period from real data?) without needing the persistence/API layers RM4's `netDebtToEbitda` fix genuinely required. Recorded here per this project's "honest account of what actually happened" discipline, not silently substituted.
2. **The new permanent unit test's initial planned fixture (`series("70", "100")`) duplicated the already-rewritten `classifyTrend_stronglyDeclining_onLargeDecrease` fixture exactly.** Caught during implementation before it became redundant test coverage: `stronglyDeclining`'s fixture was changed to `series("60", "100")` (`-40%`, still `STRONGLY_DECLINING`) so the two tests exercise distinct values while both remaining meaningful.

## Merge Gate

1. `./mvnw -o test` — **554/554 passed**, zero regressions.
2. Every Acceptance Check above is checked, with its supporting test or live evidence named.
3. `git diff --stat main` confirmed to touch only `backend/src/main/java/it/mazzoni/vis/thesis/ThesisInputBuilder.java`, `backend/src/test/java/it/mazzoni/vis/thesis/ThesisInputBuilderTest.java`, `specs/roadmap.md`, and this spec directory.
4. Manual verification is complete: real FMP data fetched live, the real fix confirmed correct and the real pre-fix bug confirmed reproduced, both by actually executing the code — disclosed deviation from the originally-planned Docker flow, with rationale.
5. `specs/roadmap.md` → Phase TA6 added under Group TA and marked `*(complete)*`, in the same merge.

## Known Risks (carried forward or introduced by this phase)

- **Historical `InvestmentThesisResult` rows generated before this phase keep their old, incorrectly-directed `revenueTrend`/`earningsTrend`/`freeCashFlowTrend` classifications** — only new generations after merge see the corrected value. Not a backfill target this phase (Decision 5), same posture as RM4's `netDebtToEbitda` fix.
- **No live Vertex AI Gemini smoke test was run this phase** (Docker unavailable) — the fix is verified at the `classifyTrend` unit level against real fetched data, not end-to-end through an actual generated thesis. The underlying evidence-field wiring (`revenueTrend`/`earningsTrend`/`freeCashFlowTrend` as citable `evidenceFields`) was already live-verified by TA4/RM4 and is unchanged by this phase — only the classification value feeding into it is corrected. A future session with Docker available could re-run RM4's own live smoke-test pattern (seed + generate + inspect `input_snapshot`) for extra end-to-end confidence, though it is not required to close this phase's own stated scope.
- **No automated check exists for whether the AI's prose reasoning about a now-correctly-directed trend is itself sound** — same disclosed gap RM4 carried forward; out of scope for a backend classification-logic fix with no prompt change attached.
