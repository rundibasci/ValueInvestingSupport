# Requirements — Phase Z3: Valuation Engine (Demo-compatible)

## Scope

Implement the three core valuation calculators needed to complete the Group Z demo milestone. These calculators operate on primitive financial inputs (no DB, no HTTP) and are data-source agnostic — they will be reused without modification in Group C (production path).

## Feature Context

- Builds on Z2's `FundamentalSnapshot` and `RatioSnapshot` domain records.
- Consumed directly by Phase Z4's `GET /demo/analyze/{symbol}` endpoint.
- Calculators are pure functions: given inputs → deterministic output. No side effects, no I/O.

## Calculators

### GrahamCalculator
- Input: `eps` (BigDecimal), `bvps` (BigDecimal)
- Output: `BigDecimal` (Graham Number)
- Formula: `√(22.5 × EPS × BVPS)`
- Returns `null` if either input is null, zero, or negative.

### DcfCalculator
- Input: `DcfInput` record — `fcfTtm`, `growthY1Y5`, `growthY6Y10`, `terminalRate`, `wacc`, `shares`, `netDebt`
- Output: `DcfResult` record — `fairValue`, `fairValueLow` (WACC+2%), `fairValueHigh` (WACC-1%), `enterpriseValue`, snapshot of input parameters
- RULE-06 guard: if fewer than 3 years of positive FCF are present in the caller's data, the calculator returns `Optional.empty()` (does **not** throw). Caller is responsible for passing `fcfYearsPositive` count.
- Scenarios: base uses provided WACC; low uses WACC+2%; high uses WACC-1%.

### MarginOfSafetyCalculator
- Input: `fairValue` (BigDecimal), `currentPrice` (BigDecimal)
- Output: `BigDecimal` (percentage, e.g. `13.6` for 13.6%)
- Formula: `(fairValue − currentPrice) / fairValue × 100`
- Returns `null` if either input is null or fairValue is zero/negative.

## Key Decisions

| Decision | Choice | Reason |
|---|---|---|
| RULE-06 behavior | Return `Optional.empty()` | Z4 endpoint must return partial results when DCF is ineligible; an exception would break the response |
| Composite base | Base DCF scenario only | Low/high are informational range bounds; blending would obscure transparency principle |
| Test data | Round invented numbers | Self-documenting tests — expected values calculable by hand, formula is the spec |

## Out of Scope

- DDM calculator (Phase C1 / production path only — requires 5-year dividend history)
- Database persistence of results (Phase C3)
- REST endpoints (Phase Z4)
- Composite fair value aggregation (Phase Z4, not Z3)
