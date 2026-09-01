# Sector-Aware Valuation Metrics — Definition Doc (REIT-First)

**Date:** 2026-09-01
**Status:** Draft for review — not yet reflected in `mission.md` or `roadmap.md`.
**Scope decision (confirmed with user):** build a general sector-aware metrics framework, REIT is the first fully-specified sector profile. NAV per share is in-scope as an explicitly-labeled cap-rate estimate, not deferred.

**Purpose:** define exactly which metrics change, why, where the data comes from, and how they flow through screener → scoring → security detail → AI thesis, before touching `specs/mission.md` / `specs/roadmap.md`. Once this doc is reviewed, its "Governance Changes" section becomes the literal source text for a new Design Principle and a new `roadmap.md` Group.

---

## 1. The problem, precisely

`ValueScoreService.determineWeightProfile` ([backend/src/main/java/it/mazzoni/vis/scoring/ValueScoreService.java:107-122](backend/src/main/java/it/mazzoni/vis/scoring/ValueScoreService.java#L107-L122)) already special-cases `sector.contains("real estate") || sector.contains("reit") || sector.contains("utilit")` into a `reit-utility` weight profile. But that profile only **reweights** the five existing pillars (MoS 30/Quality 20/Safety 30/Growth 10/Dividend 10) — it does not change what feeds them. Every pillar still computes from GAAP-earnings metrics designed for an operating company:

| Pillar | Current metric | Why it misleads for a REIT |
|---|---|---|
| MoS | Composite fair value built on DCF/Graham Number, both anchored to EPS | Real estate depreciation is large and non-cash; GAAP net income structurally understates cash available for distribution. A financially healthy REIT can show a shrinking or negative "earnings" trend while FFO grows. |
| Quality | ROIC (fallback ROE), both net-income-based ([ValueScoreService.java:142-150](backend/src/main/java/it/mazzoni/vis/scoring/ValueScoreService.java#L142-L150)) | Same depreciation distortion suppresses the numerator; a well-run REIT can score as low-quality. |
| Safety | Debt/Equity thresholds (≤0.5 → 20pt, ≤1.0 → 14pt, ≤2.0 → 7pt, else 0) ([ValueScoreService.java:153-168](backend/src/main/java/it/mazzoni/vis/scoring/ValueScoreService.java#L153-L168)) | REITs are structurally leveraged by business model (they cannot retain earnings — ≥90% of taxable income must be distributed — so equity doesn't build the way it does at an industrial company). A D/E of 1.5 is unremarkable for a REIT and punitive for most other sectors. Debt/EBITDA and interest coverage are the sector-standard leverage lens. |
| Growth | YoY revenue growth ([ValueScoreService.java:171-181](backend/src/main/java/it/mazzoni/vis/scoring/ValueScoreService.java#L171-L181)) | Revenue growth conflates lease escalations, occupancy, and acquisitions; FFO/AFFO-per-share growth is what the sector actually tracks because it nets out the depreciation and financing noise. |
| Dividend | Payout ratio on EPS ([ValueScoreService.java:184-192](backend/src/main/java/it/mazzoni/vis/scoring/ValueScoreService.java#L184-L192)) | A REIT payout ratio on EPS routinely exceeds 100% by design and tells you nothing about sustainability. AFFO payout ratio is the standard sustainability check. |

This is the same class of gap that `ThesisInputBuilder` already documents explicitly in its own class comment ([backend/src/main/java/it/mazzoni/vis/thesis/ThesisInputBuilder.java:25-35](backend/src/main/java/it/mazzoni/vis/thesis/ThesisInputBuilder.java#L25-L35)): *"EBITDA is approximated from operatingIncomeHistory (no separate depreciation/amortization figure is captured anywhere in FundamentalSnapshot) — documented here as an approximation, not a precise EBITDA calculation."* Adding a real D&A figure (Section 3) fixes that known, already-flagged gap for the whole platform, not just REITs.

---

## 2. Architecture: sector metric profiles, not just weight profiles

Today `ScoringRiskProperties.WeightProfile` is a 5-tuple of pillar *weights* keyed by a string (`reit-utility`, `financial`, `cyclical`, `dividend-paying`, `non-dividend-growth`) — see [application.yml:102-134](backend/src/main/resources/application.yml#L102-L134). Sector-aware metrics need one more layer underneath that: a **metric source** per pillar, keyed by the same sector classification.

Proposed shape (illustrative, not final Java):

```
SectorMetricProfile {
  key: "reit"                       // same classification key ValueScoreService already derives
  mosSource: MOS_FFO_MULTIPLE        // vs MOS_COMPOSITE_FAIR_VALUE (default)
  qualitySource: QUALITY_FFO_MARGIN  // vs QUALITY_ROIC_ROE (default)
  safetySource: SAFETY_DEBT_EBITDA   // vs SAFETY_DEBT_EQUITY (default)
  growthSource: GROWTH_FFO_PER_SHARE // vs GROWTH_REVENUE (default)
  dividendSource: DIVIDEND_AFFO_PAYOUT // vs DIVIDEND_EPS_PAYOUT (default)
}
```

`ValueScoreService.compute()` would resolve **both** the weight profile (unchanged) and a metric profile from the same sector key, and each `computeXScore` method would branch on metric source rather than assume one formula. This is additive: every non-REIT sector keeps its current metric source (`*_DEFAULT`) and every existing test for `financial`/`cyclical`/`dividend-paying`/`non-dividend-growth` is untouched.

Why this over a REIT-only special case: the same distortion (non-cash charges, sector-specific leverage norms) exists for banks/insurers (`financial` profile already exists but still uses generic ROE/D-E) and utilities (currently folded into `reit-utility` even though a regulated utility's economics differ from a REIT's). Building the branch point now means Group RM ships REIT as the first complete profile without a second refactor when a `financial`-sector profile gets its own metric redefinition later. That later work is explicitly **out of scope** for Group RM — this doc only defines REIT.

---

## 3. Data gap audit

**RM1 status (confirmed 2026-09-01):** verified live against FMP Premium's `/income-statement`, `/balance-sheet-statement`, `/cash-flow-statement`, `/key-metrics`, and `/ratios` endpoints for `O`, `PLD`, and `SPG` (`period=annual`). Findings below are confirmed field names, not guesses — see §5 for the one field that came back negative.

| New field needed | FMP source field (confirmed live) | Target DTO | Used for |
|---|---|---|---|
| Depreciation & amortization | `depreciationAndAmortization` — present on **both** `/income-statement` and `/cash-flow-statement`; mapped from income-statement for consistency with `ebitda` below | `FmpIncomeStatementEntry.depreciationAndAmortization` (added RM1) | FFO |
| EBITDA (precise) | `ebitda` — present directly on `/income-statement`, computed by FMP itself; **better than planned** — no `operatingIncome + D&A` derivation needed | `FmpIncomeStatementEntry.ebitda` (added RM1) | Safety pillar (Debt/EBITDA, interest coverage). FMP's `/key-metrics` even exposes a ready-made `netDebtToEBITDA`, available as a cross-check when RM2 computes it internally. |
| Gains/losses on real-estate sales | **Not isolable.** No field on any checked endpoint separates real-estate-sale gains/losses from other non-cash items; the closest are the catch-all `otherNonCashItems` (cash-flow) and `otherInvestingActivities`, neither specific enough to use without materially distorting FFO. | — | FFO's gains/losses add-back is **out of scope for RM1/RM2** — FFO ships as `Net Income + D&A` only, documented as an approximation of the full NAREIT formula (same posture as `ThesisInputBuilder`'s existing EBITDA-approximation note), not blocked on this. |
| Impairment charges | Not separately exposed by FMP on any checked endpoint. | — | Same as above — omitted, documented as approximation, not blocking. |
| Diluted shares outstanding | already present (`FmpIncomeStatementEntry.sharesOutstandingDil`) | existing | FFO/AFFO per share |
| Maintenance/recurring capex | `FmpCashFlowEntry.capitalExpenditure` already captured, but it's *total* capex, not the recurring-vs-growth split AFFO needs | existing, needs a documented split heuristic (§4.2) | AFFO |
| NOI (net operating income) | **Confirmed absent** — not present on any of the five endpoints checked, for any of the three REIT symbols. | — | Implied cap rate, NAV — **§5 Fallback C now in effect**, see below. |
| Total debt, cash | already present | `FmpBalanceSheetEntry` | Debt/EBITDA |

**Net assessment (confirmed):** FFO (D&A-only approximation), Debt/EBITDA (using FMP's own precise `ebitda`), AFFO (with the documented recurring-capex heuristic), AFFO payout ratio, and FFO/AFFO-per-share growth are all buildable from data FMP Premium already licenses us — implemented in RM1 (D&A/EBITDA ingestion; §3 mapping above) for RM2 to consume. Implied Cap Rate and NAV per share are **not buildable from FMP fundamentals data** — see §5's resolution.

---

## 4. Metric definitions — REIT profile

All formulas follow NAREIT's standard definitions (the sector-recognized standard, same posture as using GAAP for everything else — Design Principle 1, "data before opinion," extends naturally to "use the standard the sector itself uses").

### 4.1 FFO (Funds From Operations) — replaces net income as the earnings base
```
FFO = Net Income
    + Real-estate depreciation & amortization
    − Gains on sale of depreciable real estate
    + Losses on sale of depreciable real estate
    + Impairment write-downs of depreciable real estate (if available)
```
`FFO per share = FFO / diluted shares outstanding`

### 4.2 AFFO (Adjusted FFO) — replaces FCF for dividend-sustainability purposes
```
AFFO = FFO
     − recurring/maintenance capital expenditures
     − straight-line rent adjustments (if isolable; else omitted, flagged as approximation)
```
Recurring-capex split heuristic (first pass, mirrors the documented-heuristic pattern `ThesisInputBuilder.classifyTrend` already uses): if FMP does not separate maintenance from growth capex, apply the existing `maintenance-capex-depreciation-ratio: 0.70` config value already defined for the DCF engine ([application.yml:98](backend/src/main/resources/application.yml#L98)) — reuse, not a new assumption invented for this doc.

### 4.3 Valuation multiples — replace P/E as the MoS input
```
P/FFO  = Price / FFO per share
P/AFFO = Price / AFFO per share
```
MoS pillar for the REIT profile compares current P/FFO (or P/AFFO) against a peer/historical band (reuses the Group MA `ValuationHistoryService` percentile-band machinery already built for P/E/P/B/EV-EBITDA — same mechanism, new input series) rather than against a DCF-derived intrinsic value, since REIT DCF requires property-level cash flow modeling this platform does not attempt.

### 4.4 Leverage — replaces Debt/Equity in the Safety pillar
```
Net Debt / EBITDA        (target thresholds to calibrate against REIT-sector norms, not the current 0.5/1.0/2.0 D/E bands)
EBITDA / Interest expense (fixed-charge coverage)
```

### 4.5 Growth — replaces revenue growth
```
FFO-per-share YoY growth   (primary)
AFFO-per-share YoY growth  (secondary/cross-check)
```

### 4.6 Dividend sustainability — replaces EPS payout ratio
```
AFFO payout ratio = Dividends paid / AFFO
```

### 4.7 Implied Cap Rate — new standalone cross-check, not mapped to a pillar
```
Implied Cap Rate = NOI / (Market cap + Total debt − Cash)     [Enterprise Value in the denominator]
```
Displayed alongside the MoS pillar on the security-detail page as a sector-native valuation cross-check (Design Principle 2 — every output shows its inputs/formula), not folded into the score itself given the NOI data-quality risk in §5.

### 4.8 NAV per share — approximate, explicitly labeled estimate
Per the user's confirmed decision: include it, cap-rate-driven, clearly marked as an estimate.
```
Estimated Gross Asset Value = NOI / Assumed Cap Rate
Estimated NAV = Estimated Gross Asset Value − Total Debt + Cash − Preferred Equity (if any)
Estimated NAV per share = Estimated NAV / diluted shares outstanding
```
Requires: (a) a **default assumed cap rate**, config-driven (mirrors `wacc`-style config in `ValuationDefaultsProperties`, not hardcoded), since property-type-level cap rates (office vs. industrial vs. residential) are not derivable from FMP fundamentals — a single sector-level default is the honest v1 scope, with the assumption value and formula both rendered in the UI next to the number, never presented as a precise appraisal. (b) NOI itself — the highest-risk input, see §5.

---

## 5. Highest risk: NOI availability — resolved, Fallback C in effect

**RM1 finding (confirmed 2026-09-01):** NOI is confirmed absent from every endpoint checked (`/income-statement`, `/balance-sheet-statement`, `/cash-flow-statement`, `/key-metrics`, `/ratios`) for `O`, `PLD`, and `SPG` — no field, no proxy, on any of them.

Both proposed fallbacks were evaluated against the confirmed field list and rejected:
- **Fallback A** (`Revenue − Property operating expenses`) — rejected. The only expense breakdown available is `/income-statement`'s `operatingExpenses`, which is company-wide (includes G&A and corporate overhead), not property-level. `Revenue − operatingExpenses` reduces to approximately `operatingIncome`, not NOI — using it would materially overstate leverage-implied cap rates and NAV would be built on a distorted GAV, worse than shipping nothing.
- **Fallback B** (`Operating income + D&A`) — rejected for the same reason: `operatingIncome` already nets out corporate G&A that true property-level NOI would exclude differently; treating it as an EBITDA-at-the-property-level proxy would silently misrepresent Implied Cap Rate's denominator logic, not just its precision.
- **Fallback C is therefore in effect**: Implied Cap Rate (§4.7) and NAV per share (§4.8) are **out of scope for RM1 and RM2**, marked `INSUFFICIENT_DATA` per Design Principle 12 wherever they would otherwise appear, rather than published on a proxy that would misrepresent the sector it's meant to serve fairly. FFO, AFFO, Debt/EBITDA, AFFO payout ratio, and FFO/AFFO-per-share growth (§4.1–§4.6) are unaffected and proceed in RM2 as planned.

Revisiting Cap Rate/NAV is a distinct, later-phase decision gated on finding a data source with actual property-level NOI (a REIT-specialized data vendor, or per-symbol manual entry) — not assumed to be solvable with FMP's general fundamentals data, and not blocking RM2/RM3/RM4.

This mirrors exactly how `MoatAssessmentService` already handles `INSUFFICIENT_DATA` when provider ROIC history is too thin (Group MA3) — same posture, same platform convention, not a new failure mode to invent.

---

## 6. Interim mitigation, shippable immediately (before RM1 lands)

User-proposed, confirmed: a screener/security-detail **disclaimer** for REIT/real-estate (and, per open question 4, utility) securities, decoupled from the metric work above — it needs no new data, no new formulas, and can ship on its own timeline instead of waiting on §5's FMP verification.

- Reuses the existing MiFID II disclaimer pattern already in the codebase (`ValuationDetailResponse.MIFID_DISCLAIMER`, `QuickAnalysisResponse.DISCLAIMER`, rendered via the static `disclaimer` string in [ScreenerPage.tsx:7-8](frontend/src/pages/ScreenerPage.tsx#L7-L8)) — same mechanism, a second sector-scoped message, not a new UI pattern.
- Gated on the **same classification logic already in `ValueScoreService.determineWeightProfile`** ([backend/src/main/java/it/mazzoni/vis/scoring/ValueScoreService.java:107-111](backend/src/main/java/it/mazzoni/vis/scoring/ValueScoreService.java#L107-L111): `sector.contains("real estate") || sector.contains("reit") || sector.contains("utilit")`). That string-matching logic currently lives only inside `ValueScoreService` as a private method — worth extracting to a small shared `SectorClassifier` helper now, so the screener disclaimer and the future `SectorMetricProfile` resolution (§2) both call one source of truth instead of two independent copies of the same sector-string matching drifting apart over time.
- Screener: when a result row's `sector` classifies as REIT/real-estate or utility, show a caveat — e.g. *"P/E, ROE, and Debt/Equity are known to be less reliable for REIT/real-estate and utility stocks due to non-cash depreciation and sector-typical leverage. Sector-aware metrics (FFO, AFFO, Debt/EBITDA) are in development — see the security detail page for available alternatives."* Rendered per-row (badge/tooltip, `item.sector` is already returned) and/or as a banner above the results table when any visible row matches, mirroring the existing global MiFID footer rather than inventing new placement conventions.
- Security-detail / review page: same caveat next to the P/E, ROE, D/E, payout-ratio fields specifically for REIT/utility-classified securities, consistent with Design Principle 12 ("missing data must be explainable") — here it's not missing data but *misleading* data, which the current principle set doesn't quite name; §9.1's new Principle 16 closes that wording gap.
- **Ships as its own small change, ahead of Group RM's data work** — track it as Phase RM0 in §9.2 below rather than folding it into RM1, precisely because it has no dependency on the FMP field-verification open question in §5.

---

## 7. Screener / scoring integration

- `RatioSnapshot` gains new nullable fields: `ffoPerShare`, `affoPerShare`, `priceToFfo`, `priceToAffo`, `netDebtToEbitda` (precise, superseding the thesis-layer approximation once D&A exists), `interestCoverageEbitda`, `affoPayoutRatio`. NOI/cap-rate/NAV fields live in a separate nullable group so their absence (per §5 Fallback C) doesn't block persisting the rest.
- Screener gains REIT-relevant filter/sort columns: P/FFO, P/AFFO, Debt/EBITDA, AFFO payout ratio — shown instead of P/E, D/E, EPS payout for securities classified into the REIT sector profile; other sectors keep today's columns unchanged.
- `ValueScoreService` pillar methods branch on `SectorMetricProfile` (§2) for the REIT key; every other sector key falls through to the existing formulas verbatim — no behavior change for non-REIT securities, so the existing test suite for `financial`/`cyclical`/`dividend-paying`/`non-dividend-growth` profiles stays green untouched.
- Security-detail / review page: FFO/AFFO/P-FFO/P-AFFO/Debt-EBITDA/Implied Cap Rate/estimated NAV displayed for REIT-classified securities, each with its formula and inputs visible (Design Principle 2), and each field that falls back to `INSUFFICIENT_DATA` labeled per Design Principle 12 rather than silently omitted.

---

## 8. AI Thesis propagation

Per Design Principle 15, the thesis agent may only interpret fields explicitly supplied to it — so none of §4's metrics reach the AI's reasoning until they're added to the contract deliberately:

- `ThesisInput` / `thesis-input.schema.json` gain optional fields: `ffoPerShare`, `affoPerShare`, `priceToFfo`, `priceToAffo`, `affoPayoutRatio`, `netDebtToEbitda` (now precise rather than the documented operating-income approximation — `ThesisInputBuilder`'s comment at lines 25-35 should be updated to say so once D&A is available), and, if §5 resolves NOI availability, `impliedCapRate` / `estimatedNavPerShare` with the cap-rate assumption surfaced as its own field (never a bare number with no stated assumption, matching the UI requirement in §4.8).
- `system-prompt-v2.txt` needs a REIT-awareness addition: when `ffoPerShare`/`affoPerShare` are populated, the model should reason from *those* instead of treating a low/negative net-income-derived signal as a red flag on its own — this is a **prompt content change**, tracked as its own decision the same way TA2 tracked every prompt-touching decision explicitly (`specs/2026-08-27-ta2-vertex-prompt-contract/requirements.md`), not assumed here.
- `thesis-output.schema.json` and `ThesisOutput`/validator stay unchanged — this is new *input* context, not a new output shape.
- Because `thesis-input.schema.json` is currently treated as an unmodified reuse artifact under ADR-002, widening it is itself a decision that belongs in whatever roadmap phase implements this (see §9) — flagged here so it isn't missed, not resolved by this doc.

---

## 9. Proposed governance changes (draft text — not yet applied)

### 9.1 `mission.md` — new Design Principle 16

> **16. Sector-appropriate valuation metrics** — GAAP-earnings multiples (P/E, ROE/ROIC, Debt/Equity, EPS payout ratio) are structurally distorted for capital-intensive, non-cash-charge-heavy sectors. Where a sector-recognized alternative standard exists (e.g., REITs' FFO/AFFO per NAREIT), the platform computes and surfaces that standard instead of forcing GAAP metrics onto a business model they don't fit — always alongside, never in place of, the transparency and missing-data-explainability principles above (2, 12). This principle governs the `SectorMetricProfile` classification (`specs/sector-aware-valuation-metrics.md`) that feeds the Value Score pillars and the screener per sector.

Also touches:
- Principle 8 ("Financial resilience...") — add a clause that leverage/coverage norms are read against sector-appropriate benchmarks, not one universal ratio.
- Principle 11 (single-stock review packet) — add FFO/AFFO/Debt-EBITDA to the required-fields list for REIT-classified securities, alongside the existing DCF/Graham/MoS list.

### 9.2 `roadmap.md` — new Group RM (two-letter code, avoids collision with used single letters A–L)

```
## Group RM — Sector-Aware Valuation Metrics (REIT-First)

Goal: replace GAAP-earnings metrics that are structurally distorted for REITs (P/E, ROE/ROIC,
D/E, EPS payout ratio) with sector-standard cash-flow metrics (FFO, AFFO, Debt/EBITDA, AFFO
payout ratio), architected as a general SectorMetricProfile extension point so a future
Financial/Utility profile redefinition doesn't require a second refactor.
Source: specs/sector-aware-valuation-metrics.md.

### Phase RM0: Interim Sector-Metric Disclaimer *(ships independently, ahead of RM1)*
- Extract the sector-classification string-matching currently private to
  `ValueScoreService.determineWeightProfile` into a shared `SectorClassifier` helper so this
  disclaimer and the later SectorMetricProfile resolution (RM2) share one source of truth.
- Add a REIT/real-estate/utility caveat to the screener (per-row and/or banner) and to the
  security-detail page next to P/E, ROE, D/E, and payout ratio, reusing the existing
  MIFID_DISCLAIMER constant-and-render pattern. No new data or formulas required.
- No dependency on RM1's FMP verification — this phase can ship on its own schedule.

### Phase RM1: Data Ingestion Depth (D&A, EBITDA inputs)
- **First step, addressed before any metric-computation work begins** (per user direction):
  confirm live FMP Premium payload field names for D&A, gains/losses on asset sales, and NOI
  (or its nearest proxy) for a REIT validation set (O, PLD, SPG) before committing to exact
  mappings — §3/§5 of the definition doc. Resolves open questions 1–2 in §10.
- Extend FmpCashFlowEntry/FmpIncomeStatementEntry and the persisted snapshot entities with the
  confirmed fields.
- Resolve the NOI availability decision (§5): ship Fallback A/B, or scope Implied Cap
  Rate/NAV out to a later phase per Fallback C.

### Phase RM2: SectorMetricProfile & REIT Metric Computation
- Add the SectorMetricProfile abstraction (§2); wire ValueScoreService pillar computation to
  branch on it for the REIT key, unchanged fallthrough for every other sector.
- Compute FFO, AFFO, P/FFO, P/AFFO, Net Debt/EBITDA, EBITDA interest coverage, AFFO payout
  ratio, FFO/AFFO-per-share growth; persist on RatioSnapshot.
- If RM1 confirmed NOI: compute Implied Cap Rate and estimated NAV/share with configurable
  assumed cap rate, formula and assumption both exposed wherever displayed.

### Phase RM3: Screener & Security-Detail Surfacing
- Screener columns/filters switch to REIT-appropriate metrics for REIT-classified securities.
- Security-detail/review page: FFO/AFFO/P-FFO/P-AFFO/Debt-EBITDA/[Cap Rate/NAV if RM1 confirmed]
  card, each metric showing formula + inputs (Design Principle 2) and INSUFFICIENT_DATA
  labeling (Design Principle 12) where source data is missing.

### Phase RM4: AI Thesis Propagation
- Extend ThesisInput / thesis-input.schema.json with the new optional fields (§8); update
  ThesisInputBuilder's EBITDA-approximation documentation now that precise EBITDA is available.
- Prompt-content review (own decision, tracked like TA2's prompt-touching decisions) on whether
  system-prompt-v2.txt needs REIT-awareness wording so the model reasons from FFO/AFFO instead
  of flagging a depreciation-suppressed net income as a red flag.
```

(Group letter/name, exact phase count, and acceptance checklists to be finalized when this becomes a real roadmap edit — this is the content skeleton, not final formatting.)

---

## 10. Open questions before implementation starts

1. ~~**NOI availability** (§5)~~ — **Resolved 2026-09-01 (RM1):** confirmed absent from FMP fundamentals for O/PLD/SPG. Fallback C in effect — Implied Cap Rate and NAV per share are out of scope for RM1/RM2, `INSUFFICIENT_DATA`.
2. ~~**Real-estate-specific gains/losses isolation**~~ — **Resolved 2026-09-01 (RM1):** confirmed absent (only generic `otherNonCashItems`/`otherInvestingActivities` catch-alls exist). FFO ships as `Net Income + D&A` only, documented as an approximation of the full NAREIT formula.
3. **Leverage threshold calibration** — Debt/EBITDA and interest-coverage score bands (replacing the current 0.5/1.0/2.0 D/E bands) need REIT-sector reference values, not invented numbers; propose sourcing from public NAREIT/sector benchmark data during RM2, not guessing here.
4. **Utilities currently share the `reit-utility` profile** — this doc only defines the REIT metric profile. Utilities stay on the current generic metrics until a separate utility profile is scoped; `determineWeightProfile`'s combined `reit-utility` key may need to split once RM ships, so utility securities don't silently start rendering FFO/AFFO fields that don't apply to them. Flagged here, not resolved.
