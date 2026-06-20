# Requirements — Group Score: Pipeline Demo (M3.8)

## Scope

Implement the full Seed → Valuate → Score → Rank pipeline in a single authenticated admin endpoint, then prove it works with an integration test and a shareable demo script. This is **M3.8** on the roadmap.

Two phases are in scope:

| Phase | Deliverable |
|---|---|
| **Score1** | `POST /api/v1/admin/pipeline-run` endpoint with `ValueScoreService` (full 5-factor formula) |
| **Score2** | `PipelineDemoIT` integration test + `scripts/pipeline-demo.sh` curl sequence |

---

## Context

### What exists already
- `ValueScore` entity + `ValueScoreRepository` — already scaffolded in A2 (no rework needed)
- `ValuationService.calculate()` — produces `ValuationResult` with `compositeFairValue`, `marginOfSafety`, `recommendation`
- `MarketDataClient` interface — `FmpMarketDataClient` active when `MARKET_DATA_SOURCE=fmp`
- Seed + quick-analysis flow established in Val1/Val2
- H2 + Testcontainers Redis integration test pattern established in Val2

### What this phase introduces
- `ValueScoreService` — new class; same class D1 (screener) will expose individually — no duplication at merge
- `PipelineRunService` — orchestrates the end-to-end pipeline per ticker
- `PipelineController` — ADMIN-only endpoint
- `PipelineDemoIT` — integration test
- `scripts/pipeline-demo.sh` — stakeholder demo script

---

## Decisions

### Score1: Full 5-factor ValueScoreService

The `ValueScoreService` implements the complete formula immediately (not a stub). Same service class D1 will import — merge cost is zero.

**5-factor weights:**

| Sub-score | Weight | Input fields |
|---|---|---|
| MoS | 30 | `marginOfSafety` from `ValuationResult` |
| Quality | 25 | `roic`, `roe` from latest `RatioSnapshot` |
| Safety | 20 | `debtToEquity`, `currentRatio` from latest `RatioSnapshot` |
| Growth | 15 | `revenueGrowth`, `epsGrowth` from `FundamentalSnapshot` TTM vs prior year |
| Dividend | 10 | `dividendYield`, consecutive dividend years from `DividendRecord` |

**Sub-score formulas** (each sub-score ∈ [0, max_weight]):

- **MoS (0–30):** `MoS% ≥ 30 → 30`, `MoS% ≥ 15 → 20`, `MoS% ≥ 5 → 10`, `MoS% < 5 → 0`
- **Quality (0–25):** `ROIC ≥ 15% → 25`, `ROIC ≥ 10% → 18`, `ROIC ≥ 5% → 10`, `< 5% → 0` (if ROIC null, fall back to ROE with same thresholds)
- **Safety (0–20):** `D/E ≤ 0.5 → 20`, `D/E ≤ 1.0 → 14`, `D/E ≤ 2.0 → 7`, `> 2.0 → 0` (ties broken by currentRatio: +2 if ≥ 2.0)
- **Growth (0–15):** `revenue CAGR ≥ 10% → 15`, `≥ 5% → 10`, `≥ 0% → 5`, `< 0% → 0`
- **Dividend (0–10):** `yield ≥ 2% AND ≥ 5 consecutive years → 10`, `yield ≥ 1% OR ≥ 5 years → 5`, otherwise `0`

`totalScore = mosScore + qualityScore + safetyScore + growthScore + dividendScore` (max 100)

**Null safety rule:** if a required input is null/unavailable, that sub-score is set to 0 (not a failure — data may simply be absent for some securities).

### Score2: Test infrastructure

- H2 in-memory DB + Testcontainers Redis (consistent with Val2 pattern)
- Profile: `test` + `fmpkey` (requires real FMP key for the live call)
- Single ticker under test: `AAPL`

---

## Out of Scope

- No UI changes to `feature-demo.html` in this phase (FD1 is complete; a pipeline panel belongs in a future FD2 if needed)
- No caching of `ValueScore` results in Redis (D1 adds that)
- No `GET /api/v1/securities/{symbol}/score` endpoint (D1)
- No screener integration (D2)
