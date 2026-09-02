# RM3 — Screener & Security-Detail Surfacing

## Context

RM0 (`4ca4ab3`/`54f6fe7`, merged 2026-09-01) extracted `SectorClassifier` and shipped an interim GAAP-metric caveat on the screener's Sector column and the review page's data-quality notes. RM1 (`7f7a789`, merged 2026-09-01) confirmed live FMP field availability for D&A/EBITDA/interest expense and resolved NOI/cap-rate/NAV permanently out of scope (Fallback C). RM2 (`c2730ab`, merged 2026-09-02) built `SectorMetricService`, computing and persisting FFO/AFFO/P-FFO/P-AFFO/Net-Debt-EBITDA/EBITDA-interest-coverage/AFFO-payout-ratio onto `RatioSnapshot` for REIT-classified securities, and wired `ValueScoreService`'s five pillars to consume them — all with **no REST/frontend surfacing**, by RM2's own explicit decision ("No new REST endpoint... RM3 decides what to expose").

RM3 is that surfacing phase: it exposes what RM2 already computes and persists, on the two surfaces the roadmap and `specs/sector-aware-valuation-metrics.md` §7 name — the screener and the security-detail/review page — and retires RM0's generic caveat exactly where a computed replacement now sits alongside it, per §9.2's own instruction. It introduces no new computation, no new persisted field, and no new formula: every value this phase displays already exists in `ratio_snapshot` after RM2.

**Roadmap bookkeeping note carried by this phase:** `specs/roadmap.md` never marked RM0 `*(complete)*` despite it being fully merged — confirmed by inspecting `main` directly (`SectorClassifier.java`, `ScreenerResultItem.sectorMetricCaveat`, and the `ScreenerPage.tsx` badge all exist). This phase fixes that one-line gap alongside its own roadmap edit (see `plan.md` → Group 0) rather than leaving a stale-looking roadmap for whoever reads it next.

## Scope

| In scope | Out of scope |
|---|---|
| `ScreenerResultItem` gains the 7 RM2-persisted fields (read-only surfacing, no new computation) | Any new computation, formula, or `RatioSnapshot` column (RM2 already shipped these) |
| `ScreenerRequest` gains 3 REIT-specific filters: `maxPriceToFfo`, `maxNetDebtToEbitda`, `maxAffoPayoutRatio` | A `financial`- or `utility`-sector `SectorMetricProfile` (explicitly deferred by the definition doc's own §2, unchanged by this phase) |
| `priceToFfo` as a new valid screener sort field | Implied Cap Rate, estimated NAV per share — permanently out of scope per RM1's confirmed NOI-unavailability finding (Fallback C), not merely deferred to a later RM phase |
| New `SectorMetricResponse` DTO + `SecurityReviewResponse.sectorMetrics` field, populated for REIT-classified securities only | `ThesisInput`/`thesis-input.schema.json` changes and prompt-content review for REIT awareness (`RM4`) |
| Retiring the RM0 generic caveat in `dataQualityNotes` for REIT (not utility) securities, replaced by a specific note pointing at the new panel | Retiring or changing the RM0 caveat anywhere it still applies without a replacement (utility securities, and the screener's `sectorMetricCaveat` field for non-REIT sectors) |
| Frontend: 4 new screener table columns (P/FFO, P/AFFO, Debt/EBITDA, AFFO payout), 3 new filter inputs, 1 new sort header | `SecurityDetailPage.tsx` (H4 legacy tabs) — explicitly excluded, see Decisions |
| Frontend: a new "REIT Cash-Flow Metrics" panel on the Security Review page's existing `business-quality` Section, plus reusing the already-persisted `P_FFO` valuation band in the existing "Historical valuation bands" panel | Redesigning the review page's existing sections, `Section`/`Panel`/`Metric` helpers, or badge color conventions beyond reusing them |
| One-line roadmap fix: mark RM0 `*(complete)*` | Any other roadmap edit beyond RM0's and RM3's own completion markers |

## Decisions

1. **The screener's Fair Value/MoS columns are not touched.** They read from `ValuationResult` (DCF/Graham/DDM composite fair value and its margin of safety), a computation RM2 never modified — only `ValueScoreService`'s internal `mosScore` *sub-score* (0–30 points, not separately exposed as a screener column today) uses the P/FFO percentile for REIT securities. The roadmap's "switch columns... for REIT-classified securities" is satisfied by **adding** four new columns (P/FFO, P/AFFO, Debt/EBITDA, AFFO payout) rather than replacing Fair Value/MoS, because those columns never carried a GAAP metric to switch from in the first place — confirmed by reading `ScreenerResultItem`/`ScreenerService` directly rather than assuming the roadmap's wording maps onto a column that doesn't exist.
2. **The three new screener filters are REIT-only by construction, and this is disclosed, not hidden.** Setting `maxPriceToFfo`, `maxNetDebtToEbitda`, or `maxAffoPayoutRatio` implicitly excludes every non-REIT row, because those rows' corresponding `RatioSnapshot` fields are `null` and a `<=` comparison against `null` is false. This mirrors how `minRoic`/`maxDebtToEquity` already behave for a symbol missing that ratio — not a new class of behavior — but the frontend filter form must say so explicitly (`plan.md` → Group 3.3) rather than let a user wonder why setting a "Max P/FFO" filter silently removed every non-REIT company from their results.
3. **`SectorMetricResponse` is a new nested field on the existing review response, not a new endpoint.** RM2 explicitly deferred this decision to RM3; this phase resolves it by following the same pattern `moat`/`capitalAllocation`/`valuationBands` already use — one aggregated `GET /api/v1/securities/{symbol}/review` call, not a fifth REIT-specific round trip. A `null` value (non-REIT security) is distinguished from an `INSUFFICIENT_DATA`-status populated-but-empty object (a REIT security whose sector metrics haven't been computed yet, e.g. seeded before RM2's ordering fix) — never the same shape, so the frontend can tell "not applicable" apart from "applicable but missing" per Design Principle 12.
4. **Formula strings are static per-metric-group constants on the response DTO, mirroring `DerivedRoicCalculator.FORMULA`'s existing precedent exactly** — not a new convention. This is the only place in the codebase that already solved "show the formula next to the computed value" (RoicObservation's `formulaNote`), so RM3 reuses that shape rather than inventing a fresh one.
5. **The P/FFO valuation band reuses `ValuationHistoryService`'s existing `"P_FFO"` output with zero backend change** — RM2 already added this band unconditionally (it degrades to `INSUFFICIENT_DATA` for non-REIT securities via the existing `band()` null-filtering, per RM2's own `plan.md` Group 6). RM3's only job is to render it, matching `peBand`/`evEbitdaBand`'s existing `bandByMetric('PE')` lookup pattern in `BusinessQuality`. The band is gated to render only when `sectorMetrics` is non-null, so a non-REIT security never shows an always-`INSUFFICIENT_DATA` chart in a panel every other sector's securities also see.
6. **The REIT P/FFO band is labeled as a relative-valuation signal, explicitly not an intrinsic-value margin of safety**, carrying forward RM2's own `requirements.md` Decision 8 flag ("RM3's security-detail surfacing... must not present REIT MoS with the same... language the non-REIT MoS pillar uses"). This phase is where that flag gets resolved into an actual UI label, not deferred again.
7. **RM0's generic caveat is retired only for REIT securities, not utility.** `SectorClassifier.isReit` (REIT/real-estate only) now has a computed, displayed replacement (this phase); `SectorClassifier.isReitOrUtility` minus `isReit` — i.e., utility alone — still has no `SectorMetricProfile` and keeps the original generic caveat unchanged. This directly follows RM2's own `isReit`/`isReitOrUtility` split (`requirements.md` Decision 1) through to its logical conclusion at the surfacing layer.
8. **The screener's per-row `sectorMetricCaveat` field (RM0) is left unchanged in this phase.** It still fires for `isReitOrUtility` (both REIT and utility) — the screener table never showed a GAAP metric column for the caveat to sit "next to" in the first place (see Decision 1), so there is no equivalent "retire it once a replacement is shown alongside" moment on this surface the way there is in the review page's `dataQualityNotes`. Retiring or narrowing it here would remove a REIT-relevant warning without anything replacing it in the same visual context (the badge sits on the Sector column; the new P/FFO/etc. columns sit elsewhere in the row) — left as a possible follow-up, not attempted here to avoid a UI decision with no clear "done" criterion.
9. **`SecurityDetailPage.tsx` (the H4 legacy tabbed page) is explicitly out of scope**, following RM0's own precedent of leaving it untouched. It still renders P/E/ROIC/ROE/Debt-Equity charts per symbol with no caveat at all today (RM0's commit touched only `ScreenerPage.tsx` on the frontend). Since `specs/roadmap.md` and `mission.md` Design Principle 11 both treat the in-depth Review page (`/securities/:symbol/review`, H4A/H4C) as the authoritative "single-stock research packet," and RM0 already chose to rely on that page's `dataQualityNotes` mechanism rather than touch the legacy tabs page, RM3 follows the same precedent rather than silently expanding scope to a second, less-visited page. Flagged as a known, pre-existing gap (not introduced by this phase) in `validation.md` → Known Risks.
10. **No automated frontend test.** This project has no test runner configured (`package.json` has no `test` script; no vitest/jest/RTL dependency; confirmed absent project-wide, same finding TA5 and SR2 already documented). Frontend validation is `npm run build` plus documented manual QA, matching that established precedent rather than introducing a test framework as a side effect of this phase.

## Data Shapes

```java
// ScreenerResultItem — 7 new nullable fields, appended after sectorMetricCaveat
BigDecimal ffoPerShare;
BigDecimal affoPerShare;
BigDecimal priceToFfo;
BigDecimal priceToAffo;
BigDecimal netDebtToEbitda;
BigDecimal interestCoverageEbitda;
BigDecimal affoPayoutRatio;

// ScreenerRequest — 3 new nullable filter fields
BigDecimal maxPriceToFfo;
BigDecimal maxNetDebtToEbitda;
BigDecimal maxAffoPayoutRatio;

// New: SectorMetricResponse (nested on SecurityReviewResponse.sectorMetrics, nullable)
public record SectorMetricResponse(
    BigDecimal ffoPerShare, String ffoFormula,
    BigDecimal affoPerShare, String affoFormula,
    BigDecimal priceToFfo, BigDecimal priceToAffo, String valuationMultipleFormula,
    BigDecimal netDebtToEbitda, BigDecimal interestCoverageEbitda, String safetyFormula,
    BigDecimal affoPayoutRatio, String payoutFormula,
    String availabilityStatus,   // "AVAILABLE" | "INSUFFICIENT_DATA"
    String availabilityMessage   // null when AVAILABLE
) {}
```

```
GET /api/v1/securities/{symbol}/review — unchanged shape, one new nullable field:
{
  ...,
  "sectorMetrics": null | {
    "ffoPerShare": 3.9024, "ffoFormula": "...",
    "affoPerShare": 1.9897, "affoFormula": "...",
    "priceToFfo": 15.6442, "priceToAffo": 30.6830, "valuationMultipleFormula": "...",
    "netDebtToEbitda": 9.1298, "interestCoverageEbitda": 3.1088, "safetyFormula": "...",
    "affoPayoutRatio": 1.6225, "payoutFormula": "...",
    "availabilityStatus": "AVAILABLE", "availabilityMessage": null
  }
}
```

## Out of Scope

- Any new computation, formula, or `RatioSnapshot`/persisted-entity column (RM2 already shipped all seven fields this phase surfaces).
- Implied Cap Rate and estimated NAV per share — out of scope per RM1's confirmed NOI-unavailability finding (Fallback C), not revisited by this phase.
- A separate `financial`- or `utility`-sector `SectorMetricProfile` (deferred by the definition doc's own §2).
- `ThesisInput`/`thesis-input.schema.json` changes and the REIT-awareness prompt-content review (`RM4`).
- `SecurityDetailPage.tsx` (H4 legacy tabbed page) — see Decision 9.
- Narrowing the screener's per-row `sectorMetricCaveat` (RM0) badge — see Decision 8.
- Recalibrating any RM2 threshold (`SectorMetricProperties`) or fixing the AFFO/leverage calibration risks RM2's `validation.md` already flagged — this phase surfaces those known-imperfect numbers transparently (with their formula shown), it does not correct them.
- Introducing a frontend test framework (vitest/RTL) — a separate decision, not bundled into this phase.
