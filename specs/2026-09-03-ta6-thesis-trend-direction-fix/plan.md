# TA6 — Implementation Plan

## 1. Fix `ThesisInputBuilder.classifyTrend`'s array-direction assumption

`classifyTrend` ([backend/src/main/java/it/mazzoni/vis/thesis/ThesisInputBuilder.java:147-177](backend/src/main/java/it/mazzoni/vis/thesis/ThesisInputBuilder.java#L147)) currently walks `history` forward (`for (int i = 1; i < history.size(); i++)`), implicitly treating index 0 as the oldest point — the opposite of the newest-first convention `FmpAdapter`, `DemoAnalysisService`, and this same class's own (already-fixed) `deriveNetDebtToEbitda` all confirm for `revenueHistory`/`netIncomeHistory`/`fcfHistory`.

1. Immediately after the existing `history == null || history.size() < 2` guard, add:
   ```java
   List<BigDecimal> chronological = new ArrayList<>(history);
   Collections.reverse(chronological);
   ```
   (add `import java.util.Collections;` — not currently imported in this file).
2. Replace every subsequent reference to `history` in the method body with `chronological` — the `for (int i = 1; i < chronological.size(); i++)` loop, the `prev`/`curr` lookups, and the `periodChanges` accumulation logic are otherwise **unchanged**: `chronological` is now oldest-first, so `periodChanges.get(periodChanges.size() - 1)` (already the last line before the threshold comparisons) correctly becomes the true latest period-over-period change, and the existing volatility scan (`periodChanges.stream().anyMatch(...)`) is unaffected by order since it only checks magnitude, not position.
3. Update the method's Javadoc (lines 141-146) to state the convention explicitly: add a sentence noting `history` is expected newest-first (index 0 = most recent), matching `deriveNetDebtToEbitda`'s own convention below it in this same class, and that the method reverses it internally before computing period-over-period changes chronologically. This closes the exact documentation gap (no stated array-direction convention anywhere in the original Javadoc) that let the bug ship unnoticed through TA4 and RM4's own review.
4. No change to `STRONG_THRESHOLD`/`MILD_THRESHOLD`/`VOLATILITY_THRESHOLD` or to the threshold-comparison logic itself (lines 171-176) — only which period is fed into it.

## 2. Rewrite `ThesisInputBuilderTest`'s `classifyTrend_*` fixtures to genuinely newest-first order

Per `requirements.md` Decision 2 — these fixtures must actually exercise the corrected direction, not merely keep passing by coincidence.

1. `classifyTrend_stronglyGrowing_onLargeIncrease`: `series("100", "120")` → `series("120", "100")` (latest 120, prior 100, still `+20%` → `STRONGLY_GROWING`). Add a short comment: "newest-first, matching production data (FmpAdapter/DemoAnalysisService/deriveNetDebtToEbitda convention) — latest listed first."
2. `classifyTrend_growing_onModerateIncrease`: `series("100", "105")` → `series("105", "100")`.
3. `classifyTrend_stable_onSmallChange`: `series("100", "101")` → `series("101", "100")`.
4. `classifyTrend_declining_onModerateDecrease`: `series("100", "95")` → `series("95", "100")`.
5. `classifyTrend_stronglyDeclining_onLargeDecrease`: `series("100", "70")` → `series("70", "100")`.
6. `classifyTrend_volatile_whenAnyPeriodSwingExceedsThreshold`: `series("100", "200", "210")` → `series("210", "200", "100")`, with the existing comment reworded to match the new (correct) reading order: "newest-first: 210 (latest) <- 200 (+5% period-over-period) <- 100 (prior, +100% swing) — the huge older swing marks this volatile even though the latest period alone would read as merely 'growing'."
7. `classifyTrend_returnsNotAvailable_whenFewerThanTwoPoints` and `classifyTrend_recordsWarning_whenNotAvailable`: single-element/`null` inputs are order-independent — no change needed beyond confirming they still pass.
8. Add one new test, `classifyTrend_readsNewestFirst_notOldestFirst` — a fixture where reading the array in the wrong direction would produce a *different* `Trend` classification than reading it correctly (e.g. `series("70", "100")`: latest period is `70` vs prior `100` = `-30%` = `STRONGLY_DECLINING`; the pre-fix implementation would have read this as `100` vs `70`-as-prior... — construct the exact numbers during implementation so the two directions genuinely disagree on the resulting `Trend` enum value, not just the sign of a percentage that happens to land in the same band both ways). This is the test that actually fails against the pre-fix code — the six rewritten fixtures above prove the fix still gets ordinary cases right, this one proves the fix matters.

## 3. Live verification against real multi-year data

Per `requirements.md` Decision 3 and the roadmap's own explicit recommendation that synthetic 2-element fixtures can't expose a direction bug.

1. With Docker available (per RM2/RM3/RM4's own precedent), `docker compose up -d postgres redis`, run the backend locally, seed one or more real tickers already used elsewhere in this roadmap's live-verification history (`O`, `AAPL`, or another already-referenced symbol) via `POST /api/v1/admin/seed`.
2. Direct `psql` inspection of the seeded `fundamental_snapshot` row's `revenue_history`/`net_income_history`/`fcf_history` columns (or the equivalent persisted representation — confirm the actual column/JSON shape from the entity/migration during implementation rather than assuming) to read the real, newest-first multi-year values by hand and hand-compute what `revenueTrend`/`earningsTrend`/`freeCashFlowTrend` *should* be under the corrected latest-period definition.
3. Generate (or regenerate) an AI thesis for the seeded ticker(s) (`POST /api/v1/securities/{symbol}/thesis/generate`) and inspect the persisted `investment_thesis_result.input_snapshot` to confirm the three `Trend` values match the hand computation from step 2, not the pre-fix (reversed) reading.
4. If none of the readily available tickers' fetched windows happen to have a genuinely direction-sensitive history (oldest and newest period-over-period changes landing in different `Trend` bands), pick or seed an additional real ticker until at least one field on at least one ticker demonstrates the fix concretely — record whichever ticker/field ends up demonstrating it, and the before/after values, in `validation.md`.
5. Tear down the same way RM2/RM3/RM4 did (`kill` the backend process, `docker compose stop postgres redis`, no standing infrastructure or credentials left behind).

## 4. Regression check

1. `ThesisGenerationServiceTest`'s fixed `Trend.STABLE` placeholders (line 58) are order-independent (`STABLE` doesn't depend on which end of a 2-3 element array is "latest" for the specific hardcoded input it uses — confirm during implementation) and require no change; if inspection during implementation shows otherwise, update them and record why here.
2. `ThesisControllerTest`/`ThesisAdminControllerTest`/`ThesisConfigParityTest`/`VertexAiInvestmentThesisClientTest` — no `Trend`-ordering dependency found by inspection this session; confirm unchanged pass with no modification needed.
3. `MoatAssessmentService`/`MoatAssessmentServiceTest` — untouched by this phase (Decision 4); full moat test package still green as a regression guard, not because this phase changes anything there.

## 5. Validation, build, and merge

1. `cd backend && ./mvnw -o test-compile` after the code change, then `./mvnw -o test -Dtest='it.mazzoni.vis.thesis.**'` — every `ThesisInputBuilderTest` case (rewritten fixtures + the new direction-sensitive test) green.
2. `cd backend && ./mvnw -o test` — full suite green, zero regressions outside `ThesisInputBuilderTest`'s own rewritten assertions.
3. Live verification (Group 3) completed and its concrete before/after evidence recorded in `validation.md`.
4. `git diff --stat main` reviewed — `backend/src/main/java/it/mazzoni/vis/thesis/ThesisInputBuilder.java`, `backend/src/test/java/it/mazzoni/vis/thesis/ThesisInputBuilderTest.java`, `specs/roadmap.md` (TA6 addition + completion marker), and this spec directory only.
5. Add Phase TA6 to `specs/roadmap.md` under Group TA (after TA5), marked `*(complete)*` only once `validation.md`'s checklist passes, and update the RM4 Known-Risks cross-reference (or leave RM4's own text as historical record and just mark TA6 complete — confirm the lighter-touch option during implementation, since RM4's validation.md is itself a historical record of what was known *at RM4's own merge time* and arguably shouldn't be edited after the fact).
