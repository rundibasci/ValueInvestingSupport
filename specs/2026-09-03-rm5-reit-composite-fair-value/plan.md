# RM5 — Implementation Plan

## 1. `ValuationHistoryService` — add the `P_AFFO` band

[backend/src/main/java/it/mazzoni/vis/moat/ValuationHistoryService.java:44-56](backend/src/main/java/it/mazzoni/vis/moat/ValuationHistoryService.java#L44) already builds a `P_FFO` band. Add a second one immediately after it:

1. `band(security, resultDate, "P_AFFO", annuals, RatioSnapshot::getPriceToAffo, false)` — identical shape and semantics to the existing `P_FFO` line (same `higherIsCheap=false`, same additive/`INSUFFICIENT_DATA`-for-non-REIT guarantee via the existing `filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0)` in `band()`, zero new branching).
2. Update the class-level comment above the `P_FFO` line (lines 49-54) to also describe `P_AFFO`'s purpose (this phase's headline-fair-value input, not just a Value-Score-pillar input like `P_FFO`) so a future reader isn't left assuming both bands exist only for scoring.
3. Test: extend `ValuationHistoryServiceTest` (confirm it exists and its current fixture shape before assuming — RM2's own plan.md flagged this as possibly the first test for the class) with a REIT fixture carrying ≥3 years of `priceToAffo` → asserts a `P_AFFO` `ValuationBandResult` is produced with correct percentile/median; a non-REIT fixture → asserts `INSUFFICIENT_DATA` (or absent, whichever `band()`'s existing behavior actually produces for `P_FFO` today — mirror it exactly, don't invent a different convention for the new metric).

## 2. Flyway migration — `ValuationResult.affoFairValue`

1. `backend/src/main/resources/db/migration/V30__valuation_result_affo_fair_value.sql`:
   ```sql
   ALTER TABLE valuation_result ADD COLUMN affo_fair_value NUMERIC(15,4);
   ```
   (matches the existing `composite_fair_value`/`dcf_fair_value` column precision — confirm the exact existing precision from the `valuation_result` table's own migration history before assuming `NUMERIC(15,4)`, don't guess.)
2. `ValuationResult` entity ([backend/src/main/java/it/mazzoni/vis/domain/entity/ValuationResult.java](backend/src/main/java/it/mazzoni/vis/domain/entity/ValuationResult.java)): add `private BigDecimal affoFairValue;` with getter/setter, same style as the adjacent `compositeFairValue` field (line 62).
3. Test: a repository/entity round-trip test (extend whatever existing `ValuationResultRepository`/entity test covers `compositeFairValue` today, if one exists — confirm before assuming) proving the new column persists and loads correctly.

## 3. `ValuationService.calculate()` — REIT branch for `compositeFairValue`/`marginOfSafety`

[backend/src/main/java/it/mazzoni/vis/valuation/ValuationService.java:73-138](backend/src/main/java/it/mazzoni/vis/valuation/ValuationService.java#L73) currently computes `compositeFairValue` unconditionally from `computeComposite(dcfFairValue, grahamNumber, ddmFairValue, effectiveWeights)` for every security.

1. Inject two new dependencies into `ValuationService`'s constructor: `RatioSnapshotRepository` — **the entity-layer one**, `it.mazzoni.vis.domain.repository.RatioSnapshotRepository`, the same one `ThesisInputBuilder`/`SecurityReviewService` already use for RM2's persisted fields (not the domain-record one `FundamentalSnapshotRepository`/`fundamentalSnapshotRepository` already injected here serves a different purpose) — and `ValuationBandResultRepository`. Confirm neither creates a circular-bean-dependency issue with `ValuationHistoryService` (it shouldn't — `ValuationService` would only *read* persisted `ValuationBandResult` rows via the repository, not call `ValuationHistoryService` itself, since band computation and valuation computation are triggered at different points in `SeedTickerService.seedOne()`'s pipeline — confirm the actual ordering there before assuming `ValuationBandResult` rows already exist by the time `ValuationService.calculate()` runs for a freshly seeded REIT; if they don't yet (e.g. `ValuationHistoryService.compute()` runs *after* `ValuationService.calculate()` in the pipeline today), this phase's own `SeedTickerService` ordering may need the same kind of fix RM2 needed for `SectorMetricService`-before-`ValueScoreService` — check `SeedTickerService.seedOne()`'s actual call order for `ValuationHistoryService`/`ValuationService` before writing this branch, don't assume it's already correct).
2. After `dcfFairValue`/`grahamNumber`/`ddmFairValue`/`effectiveWeights` are computed (unchanged, still needed for the persisted sub-fields and, for non-REIT securities, the composite itself), add:
   ```java
   BigDecimal compositeFairValue;
   BigDecimal affoFairValue = null;
   if (SectorClassifier.isReit(security.getSector())) {
       affoFairValue = deriveAffoFairValue(security);
       compositeFairValue = affoFairValue; // null propagates deliberately — see deriveAffoFairValue's Javadoc
   } else {
       compositeFairValue = computeComposite(dcfFairValue, grahamNumber, ddmFairValue, effectiveWeights);
   }
   ```
3. New private method `deriveAffoFairValue(Security security)`:
   ```java
   /**
    * AFFO-based substitute fair value for a REIT-classified security (RM5): median historical
    * P/AFFO (this security's own trailing multiple, not a peer comparison — same "own history"
    * semantics as RM2's P/FFO Value-Score pillar) times the latest persisted AFFO per share.
    * Returns null — deliberately, never a fallback to the GAAP DCF/Graham/DDM blend — when
    * either input is unavailable: fewer than three years of priceToAffo history (P_AFFO band
    * INSUFFICIENT_DATA) or no persisted RatioSnapshot.affoPerShare yet (REIT seeded before
    * SectorMetricService ran, or before RM2 shipped). A null return here means compositeFairValue
    * and marginOfSafety are null for this run — the whole point of RM5 (specs/2026-09-03-rm5-
    * reit-composite-fair-value/requirements.md, Decision 5) is that a REIT's headline valuation
    * output must never silently reuse the GAAP-anchored composite it structurally distorts.
    */
   private BigDecimal deriveAffoFairValue(Security security) {
       ValuationBandResult pAffoBand = valuationBandResultRepository
               .findBySecurityOrderByResultDateDescMetricAsc(security).stream()
               .filter(b -> "P_AFFO".equals(b.getMetric()))
               .findFirst().orElse(null);
       if (pAffoBand == null || pAffoBand.getPosition() == ValuationBandPosition.INSUFFICIENT_DATA
               || pAffoBand.getMedianValue() == null) {
           return null;
       }
       BigDecimal affoPerShare = ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(security)
               .map(RatioSnapshot::getAffoPerShare).orElse(null);
       if (affoPerShare == null || affoPerShare.compareTo(BigDecimal.ZERO) <= 0) {
           return null;
       }
       return pAffoBand.getMedianValue().multiply(affoPerShare).setScale(4, RoundingMode.HALF_UP);
   }
   ```
   (confirm `ValuationBandResult`'s actual getter names — `getMedianValue()`/`getPosition()`/`getMetric()` — against the real entity before assuming, and confirm the entity-layer `RatioSnapshot`'s import doesn't collide with the domain-record `RatioSnapshot` already imported in this file the same way `ThesisInputBuilder` had to resolve that exact ambiguity in RM4, Group 2.4 of that phase's own plan.md.)
4. `mos = MarginOfSafetyCalculator.compute(compositeFairValue, currentPrice)` — **unchanged call**, already null-safe (`compute` returns `null` if `fairValue` is `null`), so no extra guard needed here; the null-propagation guarantee comes entirely from step 2-3 above.
5. `result.setAffoFairValue(affoFairValue);` — always set (null or a real value), regardless of sector, so the security-detail page can distinguish "not a REIT" from "REIT but insufficient AFFO data" from "REIT with a computed AFFO fair value" cleanly from this one field's presence/absence rather than re-deriving `isReit` client-side.
6. `dcfFairValue`/`grahamNumber`/`ddmFairValue`/`epvFairValue`/`ownerEarnings`/`waccResult` computation and persistence: **entirely unchanged**, for every sector including REIT — confirm by inspection that no other line in `calculate()` reads `compositeFairValue` before this branch (i.e., the branch is a pure post-hoc override of one local variable, not a restructuring of the method).
7. Tests, extending `ValuationServiceTest`:
   - `calculate_reitWithSufficientAffoData_usesAffoFairValueAsComposite` — REIT fixture with a mocked `ValuationBandResultRepository` returning a populated `P_AFFO` band and a mocked `RatioSnapshotRepository` returning a non-null `affoPerShare`; asserts `compositeFairValue`/`affoFairValue` equal the hand-computed `median × affoPerShare`, and `marginOfSafety` matches `MarginOfSafetyCalculator.compute` on that same figure.
   - `calculate_reitWithInsufficientAffoData_leavesCompositeFairValueAndMarginOfSafetyNull` — REIT fixture, `P_AFFO` band `INSUFFICIENT_DATA` (or absent) → `compositeFairValue`, `marginOfSafety`, `affoFairValue` all `null`; `dcfFairValue`/`grahamNumber`/`ddmFairValue` still populated as normal (regression guard: confirms this phase doesn't accidentally suppress the sub-fields too).
   - `calculate_nonReitSecurity_leavesCompositeFairValueFromGaapBlendUnchanged` — existing non-REIT test fixtures must pass **unmodified in their assertions** (only a constructor-arg-count update if the constructor signature changes) — the core regression guard that this phase is additive for every non-REIT sector, matching every prior RM phase's own regression discipline.

## 4. RM0/RM3 caveat — one added sentence, REIT branch only

[backend/src/main/java/it/mazzoni/vis/security/SecurityReviewService.java:618-625](backend/src/main/java/it/mazzoni/vis/security/SecurityReviewService.java#L618) and `SectorClassifier.REIT_UTILITY_METRIC_CAVEAT` ([backend/src/main/java/it/mazzoni/vis/common/SectorClassifier.java:20-25](backend/src/main/java/it/mazzoni/vis/common/SectorClassifier.java#L20)):

1. `SecurityReviewService.buildNotes`'s REIT-branch note (the `isReit` one, not the `isReitOrUtility` one — utilities are unaffected by this phase, per requirements.md Decision 8) gets one sentence appended: something to the effect of *"Margin of Safety and Fair Value above are computed from an AFFO-based multiple for this sector (not the GAAP-based DCF/Graham/DDM composite used elsewhere), or shown as unavailable when insufficient AFFO history exists — see the Moat and Business Quality section."* Word this precisely during implementation to match the existing note's tone and the actual UI section name (confirm the frontend still calls it "Moat and Business Quality" before citing it here — RM3's own plan.md named it that way, but verify it wasn't renamed since).
2. `SectorClassifier.REIT_UTILITY_METRIC_CAVEAT` (the `isReitOrUtility` generic string, still shown for utilities and for a REIT falling through to the `else if` in `buildNotes` — confirm which branch actually fires first for a REIT after RM3's own change, since `buildNotes` checks `isReit` before `isReitOrUtility` per the existing `if/else if` — a REIT should already be hitting the specific branch from step 1, not this generic one; this constant may need **no change** if REITs never reach it. Confirm by inspection rather than editing defensively.)
3. Test: extend whatever existing test covers `SecurityReviewService.buildNotes`'s REIT branch (or `SecurityReviewServiceTest` generally) to assert the new sentence is present for a REIT fixture, and that a utility fixture's note is **unchanged** (regression guard — utilities are out of scope, Decision 8).

## 5. Frontend — confirm zero required change, fix only if a hardcoded assumption is found

`ThesisInputBuilder` needs no change (requirements.md, confirmed by inspection). The frontend likely needs none either, since `compositeFairValue`/`marginOfSafety` are read from the same API response fields (`ValuationResult`'s JSON serialization) regardless of sector — but confirm this by inspection, don't assume:

1. `frontend/src/pages/ScreenerPage.tsx`, `frontend/src/pages/SecurityDetailPage.tsx`, `frontend/src/pages/SecurityReviewPage.tsx`, `frontend/src/pages/DashboardPage.tsx`, `frontend/src/pages/AuditPage.tsx`, `frontend/src/pages/PortfolioPage.tsx`, `frontend/src/pages/ChecklistPage.tsx` all reference `compositeFairValue`/`marginOfSafety` somewhere (confirmed present by this phase's own repo-wide grep) — read each occurrence and confirm none of them hardcodes a DCF/Graham/DDM-specific formula label or tooltip text that would now be wrong for a REIT (Design Principle 2: "show the formula and inputs" — if any of these pages currently renders a fixed "DCF / Graham Number / DDM weighted average" explanation next to the Fair Value figure unconditionally, it needs a REIT-branch variant referencing AFFO instead, mirroring however RM3 already handled this for the FFO/AFFO/P-FFO/P-AFFO card).
2. If `SecurityDetailPage.tsx`/`SecurityReviewPage.tsx` has a Fair Value / Margin of Safety card with an explanatory tooltip or formula string, and it's REIT-aware already (e.g. keyed off `sectorMetrics` presence from RM3), no change needed beyond confirming the new `affoFairValue` field (if surfaced in the API response DTO — confirm whether `ValuationResult`'s API DTO even needs to expose the new field separately, or whether `compositeFairValue` alone is sufficient for the UI's existing rendering) doesn't need its own new UI element. Default to **not** adding a new UI element unless the formula-transparency requirement (Design Principle 2) is not already satisfiable from the existing `compositeFairValue` figure plus RM3's already-shipped FFO/AFFO card.
3. No test-suite-level frontend change anticipated unless step 1's inspection finds an actual hardcoded formula string — record the finding either way in `validation.md`.

## 6. Regression check

1. `ValuationServiceTest`'s full existing suite (non-REIT fixtures) must pass unmodified in its assertions.
2. `SeedTickerService`'s existing integration/ordering tests (confirm which ones exist — RM2's own Group 2 found and fixed a real `SectorMetricService`-before-`ValueScoreService` ordering bug; this phase's Group 3.1 flags the same class of risk for `ValuationHistoryService`-before-`ValuationService` and must confirm, not assume, the actual ordering) stay green.
3. `ThesisInputBuilderTest`, `ThesisGenerationServiceTest`: confirm unmodified pass — this phase makes no code change to either, but the full backend suite run in Group 7 is the actual proof.

## 7. Validation, build, and merge

1. `cd backend && ./mvnw -o test` — full suite green, zero regressions outside `ValuationServiceTest`/`ValuationHistoryServiceTest`/`SecurityReviewServiceTest` (plus their new cases) and the new migration/entity round-trip test.
2. Live verification (Docker available — confirm before assuming, same caveat TA6 hit this session): re-seed `O` (or another REIT already used elsewhere in this roadmap's live-verification history) and a non-REIT regression check (`AAPL`), inspect the persisted `valuation_result` row directly (`psql`) for `affo_fair_value`/`composite_fair_value`/`margin_of_safety`, hand-verify the AFFO-multiple arithmetic against real `ratio_snapshot`/`valuation_band_result` rows, and regenerate an AI thesis for `O` to confirm its bear/bull case now reasons from the corrected `marginOfSafetyPercent` (no code change needed there, but this is the actual proof the fix propagates end-to-end as designed, matching this phase's whole reason for existing).
3. `git diff --stat main` reviewed — `backend/`, `specs/roadmap.md` (RM5 completion marker, and un-mark Group RM's own `(complete)` suffix on its header since this phase reopens it), possibly `frontend/` only if Group 5's inspection found a real hardcoded-formula issue, and this spec directory.
4. Update `specs/roadmap.md` → add Phase RM5 under Group RM, mark `*(complete)*` only once `validation.md`'s checklist passes; update the Group RM header to drop its own `(complete)` suffix (or reword it) since a new phase now follows RM4 in the same group, matching how this project's roadmap conventions treat a reopened group (confirm there isn't already an established convention for this from an earlier reopened group before inventing one here).
