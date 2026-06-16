# Requirements — Phase C3: Composite Fair Value & Margin of Safety

## Scope

Implement `ValuationService.calculate(symbol, params) → ValuationResult` — the orchestrator that
calls the three individual calculators (DCF, Graham, DDM), blends them into a composite fair value,
computes Margin of Safety, derives a recommendation, and persists the result to the DB.

Expose the result via `POST /api/v1/securities/{symbol}/valuation/dcf` (auth required, all roles).

**In scope:**
- `ValuationWeightsProperties` `@ConfigurationProperties` bean
- `ValuationService` with full composite + MoS logic
- `POST /api/v1/securities/{symbol}/valuation/dcf` endpoint
- `ValuationRequest` / `ValuationResponse` DTOs
- Unit tests for ValuationService (mocked repos) and MockMvc test for the controller

**Out of scope:**
- `GET /api/v1/securities/{symbol}/valuation` — that is E3 (detail screen)
- `GET /api/v1/securities/{symbol}/quick-analysis` — that is Val1
- Live FMP or Redis data fetching — C3 reads only from the DB

---

## Decisions

| Decision | Answer | Rationale |
|---|---|---|
| DDM weight fallback (DDM ineligible) | Proportional normalization | DCF: 60/(60+25) ≈ 70.59%, Graham: 25/(60+25) ≈ 29.41% |
| Weight configurability | `@ConfigurationProperties` in `application.yml` | Changeable per environment without code change |
| Endpoint path | `POST /api/v1/securities/{symbol}/valuation/dcf` | Follows roadmap literally; E3 will add the GET detail endpoint |

---

## Data Sources (DB reads only)

| Data needed | Source |
|---|---|
| `eps` (diluted) for Graham | `FundamentalSnapshot` — TTM preferred, fallback latest ANNUAL |
| `bvps` | Computed: `totalEquity / sharesOutstanding` from same snapshot |
| `fcfTtm` | `FundamentalSnapshot` — TTM preferred, fallback latest ANNUAL `freeCashFlow` |
| `fcfYearsPositive` | Count of last 3+ ANNUAL `FundamentalSnapshot` records with `freeCashFlow > 0` |
| `netDebt` | Computed: `totalDebt - cash` from same snapshot |
| `sharesOutstanding` | `FundamentalSnapshot` |
| `dpsTtm` | Sum of `DividendRecord.amount` for the last 12 calendar months |
| `consecutiveDividendYears` | Count of distinct calendar years in `DividendRecord` with ≥ 1 payment, looking back from today, stopping at first gap year (max 10y lookback) |
| `currentPrice` | Most recent `PriceQuote.close` for the symbol |

---

## Request Body (`ValuationParams`)

```json
{
  "wacc": 0.09,
  "growthY1Y5": 0.08,
  "growthY6Y10": 0.04,
  "terminalRate": 0.025,
  "requiredReturn": 0.10,
  "dividendGrowthRate": 0.05
}
```

- `wacc`, `growthY1Y5`, `growthY6Y10`, `terminalRate` — required; used for DCF
- `requiredReturn`, `dividendGrowthRate` — optional; if either is absent DDM is skipped entirely (not attempted)

---

## Composite Fair Value Logic

### Weights (configured in `application.yml`)
```yaml
valuation:
  weights:
    dcf: 0.60
    graham: 0.25
    ddm: 0.15
```

### Normalization rules

When a model cannot produce a result, its weight is redistributed proportionally among the remaining
eligible models:

| Models available | Effective weights |
|---|---|
| DCF + Graham + DDM | DCF 60%, Graham 25%, DDM 15% |
| DCF + Graham (DDM absent) | DCF 70.59%, Graham 29.41% |
| DCF + DDM (Graham absent) | DCF 80%, DDM 20% |
| Graham + DDM (DCF absent) | Graham 62.5%, DDM 37.5% |
| DCF only | DCF 100% |
| Graham only | Graham 100% |
| DDM only | DDM 100% |
| None | 422 Unprocessable Entity — cannot compute valuation |

### Model eligibility guards

| Model | Guard | Behaviour on failure |
|---|---|---|
| DCF | RULE-06: `fcfYearsPositive < 3` | `DcfCalculator` returns `Optional.empty()` → DCF excluded |
| Graham | RULE-??†: `eps ≤ 0` or `bvps ≤ 0` | `GrahamCalculator` throws `GrahamNotApplicableException` → caught → Graham excluded |
| DDM | RULE-07: `consecutiveDividendYears < 5` | `DdmCalculator` throws `DdmNotEligibleException` → caught → DDM excluded |
| DDM | `requiredReturn ≤ dividendGrowthRate` | `DdmCalculator` throws `DdmNotApplicableException` → caught → DDM excluded |
| DDM | params not provided | Params null check → DDM excluded before DB query |

†GrahamNotApplicableException already guards this; no explicit rule number in roadmap.

---

## Recommendation Thresholds

Derived from `marginOfSafety` (percentage):

| MoS | Recommendation |
|---|---|
| ≥ 25% | `STRONG_BUY` |
| ≥ 10% and < 25% | `QUALITY_VALUE` |
| ≥ 0% and < 10% | `FAIR_VALUE` |
| < 0% (price > fair value) | `OVERVALUED` |
| `marginOfSafety` is null (no price or no composite) | `null` |

---

## Response Body

```json
{
  "symbol": "AAPL",
  "valuationDate": "2026-06-16",
  "dcfFairValue": 210.50,
  "dcfFairValueLow": 185.00,
  "dcfFairValueHigh": 230.00,
  "grahamNumber": 148.32,
  "ddmFairValue": null,
  "compositeFairValue": 190.84,
  "currentPrice": 182.50,
  "marginOfSafety": 4.36,
  "recommendation": "FAIR_VALUE",
  "disclaimer": "This is a decision-support tool, not investment advice (MiFID II).",
  "weights": {
    "dcf": 0.7059,
    "graham": 0.2941,
    "ddm": 0.0
  }
}
```

`weights` reflects the effective (normalized) weights actually used — aids transparency (Design Principle 2).

---

## Error Cases

| Condition | HTTP status | Detail |
|---|---|---|
| Symbol not in `security` table | 404 | `"Symbol not found: {symbol}"` |
| No `FundamentalSnapshot` exists for symbol | 422 | `"No fundamental data available for: {symbol}"` |
| All models ineligible (composite null) | 422 | `"No valuation model applicable for: {symbol}"` |
| Validation failure on request body | 400 | Bean Validation messages |

---

## Security

- Endpoint requires authentication. All roles (`ADMIN`, `ADVISOR`, `INVESTOR`) are permitted.
- No role-specific restrictions on which symbols may be queried.

---

## Context & Dependencies

- `MarginOfSafetyCalculator` already exists at `it.mazzoni.vis.valuation.MarginOfSafetyCalculator`
- `ValuationResult` entity + `ValuationResultRepository` exist from A2
- `DcfCalculator`, `GrahamCalculator`, `DdmCalculator` exist from C1/C2
- `Recommendation` enum exists at `it.mazzoni.vis.domain.entity.Recommendation`
- C3 is the direct dependency of **Val1** (Group Val) — `ValuationService` is what Val1 calls
- `ValuationService` introduced here is also reused by **Score1** (Group Score) — no rework at merge
