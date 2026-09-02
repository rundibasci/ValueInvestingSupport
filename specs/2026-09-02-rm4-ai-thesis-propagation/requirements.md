# RM4 — AI Thesis Propagation

## Context

RM0 (merged 2026-09-01) shipped an interim GAAP-metric caveat and `SectorClassifier`. RM1 (merged 2026-09-01) confirmed live FMP field availability for D&A/EBITDA/interest expense, resolving NOI/cap-rate/NAV permanently out of scope. RM2 (merged 2026-09-02) built `SectorMetricService`, computing and persisting FFO/AFFO/P-FFO/P-AFFO/Net-Debt-EBITDA/EBITDA-interest-coverage/AFFO-payout-ratio onto `RatioSnapshot` for REIT-classified securities, and wired `ValueScoreService`'s five pillars to consume them. RM3 (merged 2026-09-02) surfaced those same seven fields on the screener and the security-detail/review page.

RM4 is the last phase in Group RM: it propagates the same already-computed REIT metrics to the AI Investment Thesis input (`ThesisInput`, Group TA's Vertex AI Gemini integration), so the agent reasons from FFO/AFFO context for a REIT security instead of only seeing GAAP-earnings-derived signals that a REIT's non-cash depreciation structurally suppresses. Like RM3, it introduces no new computation on the metrics themselves — every REIT value this phase adds to `ThesisInput` already exists in `ratio_snapshot` after RM2. It does introduce one new computation: switching the pre-existing `netDebtToEbitda` field's derivation from an EBITDA *approximation* (`operatingIncomeHistory`) to the real, FMP-computed `ebitdaHistory` RM1 already added platform-wide — this is general, not REIT-gated, per the roadmap's own "a now-precise `netDebtToEbitda` now that a real D&A figure exists" framing.

**Roadmap-text correction carried by this phase:** the roadmap's RM4 bullet says to update `system-prompt-v2.txt`; the live prompt on both sides (`backend/src/main/resources/prompts/system-prompt-v3.txt`, `vis-model-training/prompts/system-prompt-v3.txt`) is already v3, confirmed byte-identical. This is stale prose from RM4's original scoping, not a link this phase needs to preserve — see `plan.md` → Group 0.

## Scope

| In scope | Out of scope |
|---|---|
| `ThesisInput` gains 5 new optional `BigDecimal` fields: `ffoPerShare`, `affoPerShare`, `priceToFfo`, `priceToAffo`, `affoPayoutRatio` — read-only propagation of RM2-computed `RatioSnapshot` values, no new computation | Any new REIT-metric computation, formula, or `RatioSnapshot`/persisted-entity column (RM2 already shipped all seven fields this phase propagates) |
| `thesis-input.schema.json` gains the same 5 optional nullable properties | Implied Cap Rate, estimated NAV per share — out of scope per RM1's confirmed NOI-unavailability finding, unchanged by this phase |
| `ThesisInputBuilder`'s `netDebtToEbitda` derivation switches from `operatingIncomeHistory` (approximation) to `ebitdaHistory` (RM1's real EBITDA figure) — general fix, every sector | Recalibrating any RM2 `SectorMetricProperties` threshold or fixing RM2's disclosed AFFO/leverage calibration risks — this phase propagates those known-imperfect numbers transparently, it does not correct them (same posture RM3 took) |
| The 5 new fields (plus the now-precise `netDebtToEbitda`) become citable `evidenceFields`: `EvidenceField` enum, `ThesisResponseSchema.EVIDENCE_FIELDS`, `thesis-output.schema.json`'s `evidenceFields` enum, `config/vertex-gemini-v1.json`'s regenerated `responseSchema`, the backend's checked-in parity fixture, and the prompt's "only allowed values" list — all five in the existing parity chain | A separate `financial`- or `utility`-sector `SectorMetricProfile` (deferred by the definition doc's own §2, unchanged by this phase) |
| Prompt-content review (own explicit decision, tracked here) — concludes REIT-awareness wording is needed; one new numbered rule added to `system-prompt-v3.txt` (both copies, in place) | A prompt-quality evaluation harness or TRAIN-03/04-style benchmark run against the new rule — no such harness exists for a single-rule prompt edit; verified instead by one live smoke-test reading, disclosed honestly |
| `ThesisInputBuilder` constructor gains a fourth parameter (`RatioSnapshotRepository`, entity-layer) to reach RM2's persisted REIT fields, gated on `SectorClassifier.isReit` — zero added repository calls for non-REIT securities | Any change to `MarketDataClient`, the domain-record `RatioSnapshot` (`it.mazzoni.vis.domain.RatioSnapshot`), or `SectorMetricService` itself |
| One live Vertex AI smoke test (REIT + non-REIT) as part of this phase's own validation | A new `ThesisController` endpoint, a new REST surface, or any frontend change — RM4 is backend-and-prompt-only, matching the roadmap's own phase description |

## Decisions

1. **The 5 new REIT fields are citable `evidenceFields`, not context-only.** Confirmed by explicit choice this session. Mission.md Principle 15 requires "every claim traceable to a supplied input field" — without citability, the model can see `ffoPerShare`/`affoPayoutRatio` in its JSON input but has no mechanism to attribute a REIT-specific bull/bear claim to them, which defeats the roadmap's own stated goal ("reasons from FFO/AFFO instead of flagging a depreciation-suppressed net income as a red flag"). This is the more invasive of the two options considered (it touches five files across two repos instead of one), chosen because the narrower option would ship fields the agent cannot actually use in its structured reasoning output.
2. **The prompt edit lands in place on `system-prompt-v3.txt`, not a new v4.** Confirmed by explicit choice this session. No other artifact in the pipeline (Vertex config, backend loader, training scripts) pins "v3" as a version number that would need bumping — `ThesisPromptLoader.RESOURCE_PATH` is the only place the filename is referenced on the Java side, and a version bump would only add a rename step with no parity benefit. This differs from the project's general "v1→v2→v3" versioning convention for prompts (`critic-prompt-v1..v4.txt`, `system-prompt-v1..v3.txt` all exist as separate files) but is deliberate for this phase: those version bumps mark experiment checkpoints (TA2/TA3's own iteration history); this is a single additive rule with no experiment to preserve a prior version of.
3. **`netDebtToEbitda`'s derivation fix is general (every sector), not REIT-gated**, per explicit choice this session and the roadmap's own wording. `FundamentalSnapshot.ebitdaHistory()` (RM1) is platform-wide, not a REIT-specific field — using it for every security removes a documented approximation now that a precise input exists (mission.md Principle 2, transparency), and there is no reason to keep the old, less-accurate derivation for non-REIT securities once the precise one is available. **A minor, disclosed discrepancy is expected and accepted, not a bug to fix in this phase:** `ThesisInputBuilder`'s `netDebtToEbitda` reads `FundamentalSnapshot.netDebt()` directly (a single provider-supplied field), while RM2's REIT-specific `RatioSnapshot.netDebtToEbitda` computes `totalDebt − cash` itself; both are legitimate net-debt derivations already independently established in this codebase before RM4 (the former predates RM2, the latter is RM2's own choice, `specs/2026-09-02-rm2-sector-metric-profile/plan.md` Group 4). RM4 does not unify them — that would be a change to RM2's own computation, out of scope here.
4. **`ThesisInputBuilder` reaches RM2's REIT fields via the entity-layer `RatioSnapshotRepository`, not `MarketDataClient`.** `MarketDataClient.getRatios(symbol)` returns the domain-record `it.mazzoni.vis.domain.RatioSnapshot`, which RM2 never extended (RM2 only added the seven fields to the JPA **entity** `it.mazzoni.vis.domain.entity.RatioSnapshot`, populated by `SectorMetricService` and read elsewhere via `RatioSnapshotRepository`, e.g. `SecurityReviewService`). `ThesisInputBuilder` follows that same, already-established precedent rather than inventing a third access path.
5. **Non-REIT securities never query `RatioSnapshotRepository` at all** — the lookup is gated on `SectorClassifier.isReit(security.getSector())` before the repository call, not after. Confirmed by a dedicated `verifyNoInteractions` regression test (`plan.md` Group 2.6) — this is a deliberate zero-added-load guarantee for the ~90%+ of securities that aren't REITs, not an incidental optimization.
6. **A REIT security whose `RatioSnapshot` still has null RM2 fields (pre-ordering-fix seed, or seeded before RM2 shipped) produces `null` for the five new `ThesisInput` fields and adds no new `deterministicWarnings` entry.** `ThesisInput.deterministicWarnings` stays reserved for genuine data-fetch failures (the existing `safeFundamentals`/`safeRatios` catch-block convention) — the user-facing signal for "REIT metrics not yet computed" is RM3's `SectorMetricResponse.availabilityStatus` on the review page, not a second, AI-thesis-specific warning mechanism. This mirrors `ThesisInput`'s existing null-safety posture for every other optional field (e.g. `dividendYieldPercent`/`payoutRatioPercent` already go null silently when `ratios` is null).
7. **Live Vertex AI smoke test included in this phase's validation**, per explicit choice this session, matching RM2/RM3's own precedent of live end-to-end verification when API access is available. A REIT (`O`) and a non-REIT (`AAPL`) are both exercised; the REIT thesis is read manually for whether it actually cites a new evidence field and avoids over-reading depreciation-suppressed earnings, since no automated eval harness exists for prompt-content quality in this codebase.
8. **The evidence-field parity chain is regenerated, not hand-edited, at the `config/vertex-gemini-v1.json` layer.** `vis_training.vertex.schema_adapter.to_vertex_response_schema` is the only correct source for that file's `responseSchema` block (enforced by the Python test `test_checked_in_config_response_schema_matches_live_adapter_output`, byte-equality against a live re-derivation) — this phase runs the adapter against the updated `thesis-output.schema.json` and pastes its output, rather than manually inserting five enum strings into the checked-in JSON and hoping the shapes still match.

## Data Shapes

```java
// ThesisInput — 5 new nullable fields, appended after netDebtToEbitda
public record ThesisInput(
        String symbol,
        String companyName,
        LocalDate analysisDate,
        BigDecimal marketPrice,
        BigDecimal intrinsicValue,
        BigDecimal marginOfSafetyPercent,
        BigDecimal valueScore,
        BigDecimal dividendYieldPercent,
        BigDecimal payoutRatioPercent,
        BigDecimal netDebtToEbitda,       // unchanged shape, now ebitdaHistory-derived (Decision 3)
        BigDecimal ffoPerShare,           // new
        BigDecimal affoPerShare,          // new
        BigDecimal priceToFfo,            // new
        BigDecimal priceToAffo,           // new
        BigDecimal affoPayoutRatio,       // new
        Trend revenueTrend,
        Trend earningsTrend,
        Trend freeCashFlowTrend,
        DataQuality dataQuality,
        List<String> deterministicWarnings
) {}
```

```json
// thesis-input.schema.json — 5 new optional properties, same shape as existing netDebtToEbitda
"ffoPerShare":     { "type": ["number", "null"] },
"affoPerShare":    { "type": ["number", "null"] },
"priceToFfo":      { "type": ["number", "null"] },
"priceToAffo":     { "type": ["number", "null"] },
"affoPayoutRatio": { "type": ["number", "null"] }
```

```
// evidenceFields enum (thesis-output.schema.json, config/vertex-gemini-v1.json,
// vertex-gemini-v1-fixture.json, ThesisResponseSchema.EVIDENCE_FIELDS, EvidenceField enum,
// system-prompt-v3.txt) — 12 existing + 5 new = 17 total:
marketPrice, intrinsicValue, marginOfSafetyPercent, valueScore, dividendYieldPercent,
payoutRatioPercent, netDebtToEbitda, ffoPerShare, affoPerShare, priceToFfo, priceToAffo,
affoPayoutRatio, revenueTrend, earningsTrend, freeCashFlowTrend, dataQuality,
deterministicWarnings
```

## Out of Scope

- Any new REIT-metric computation, formula, or persisted column — RM2 already shipped FFO/AFFO/P-FFO/P-AFFO/Net-Debt-EBITDA/EBITDA-interest-coverage/AFFO-payout-ratio; RM4 only propagates 5 of those 7 to `ThesisInput` (EBITDA interest coverage and the raw REIT-specific Net-Debt-EBITDA are not added — the general `netDebtToEbitda` fix (Decision 3) already covers the leverage signal for every sector including REITs, and the roadmap's own RM4 bullet lists exactly the five FFO/AFFO/valuation/payout fields plus "a now-precise `netDebtToEbitda`", not EBITDA interest coverage).
- Implied Cap Rate, estimated NAV per share — permanently out of scope per RM1's confirmed NOI-unavailability finding (Fallback C), not revisited by this phase.
- Recalibrating RM2's `SectorMetricProperties` thresholds or fixing its disclosed AFFO/leverage calibration risks.
- A separate `financial`- or `utility`-sector `SectorMetricProfile`.
- Any frontend change, new REST endpoint, or `ThesisController` route change.
- A prompt-quality evaluation harness / TRAIN-03/04-style benchmark run for the new prompt rule.
- Unifying `ThesisInputBuilder`'s `netDebtToEbitda` derivation with RM2's `RatioSnapshot.netDebtToEbitda` computation (Decision 3's disclosed, accepted discrepancy).
- Backfilling `netDebtToEbitda` recomputation into historical `InvestmentThesisResult` rows already generated before this phase — only new thesis-generation calls after this phase's merge see the precise value.
