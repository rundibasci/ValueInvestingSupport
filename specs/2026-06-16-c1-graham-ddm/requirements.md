# Requirements — Phase C1: Graham Number & DDM

## Context

Phase C1 introduces the first two pure math calculators in the Valuation Engine. These are stateless, domain-agnostic classes that accept raw financial numbers and return a fair value. No JPA entities, no Spring beans, no FMP calls — just the formula. `ValuationService` (C3) will be responsible for extracting inputs from domain entities and calling these calculators.

Prerequisite phases: A2 (domain entities exist), B1/B2 (data in DB). Calculators themselves have zero runtime dependencies and can be written and tested independently of the data pipeline.

## Scope

### GrahamCalculator

- Method: `GrahamCalculator.calculate(BigDecimal eps, BigDecimal bvps) → BigDecimal`
- Formula: `Graham Number = √(22.5 × EPS × BVPS)`
- Guard: if `eps ≤ 0` or `bvps ≤ 0`, throw `GrahamNotApplicableException` (non-negative EPS and BVPS required for a real square root)
- Return value: rounded to 2 decimal places, `HALF_UP`
- Pure static utility or Spring `@Component` with no state — either is acceptable; consistency with C2/C3 style preferred

### DdmCalculator

- Method: `DdmCalculator.calculate(BigDecimal dpsTtm, BigDecimal dividendGrowthRate, BigDecimal requiredReturn, int consecutiveDividendYears) → BigDecimal`
- Formula: Gordon Growth Model — `FairValue = DPS / (requiredReturn − growthRate)`
- RULE-07 guard: if `consecutiveDividendYears < 5`, throw `DdmNotEligibleException` before doing any arithmetic
- Guard: if `requiredReturn ≤ growthRate`, throw `DdmNotApplicableException` (division by zero / negative denominator)
- Return value: rounded to 2 decimal places, `HALF_UP`

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Domain coupling | None — pure math | Mapping FundamentalSnapshot → inputs is C3's job; keeps C1 testable in total isolation |
| RULE-07 guard placement | Inside `DdmCalculator` (throws) | Caller (ValuationService) catches `DdmNotEligibleException` and omits DDM from composite; keeps eligibility co-located with the rule it enforces |
| Exception hierarchy | Unchecked (`RuntimeException`) subclasses | Consistent with Spring idioms; callers opt in to handling, not forced |
| Rounding | `BigDecimal`, `HALF_UP`, 2dp | Matches financial display precision; avoids floating-point drift |
| Test reference stocks | AAPL (Graham), KO (DDM) | KO has a long unbroken dividend history (DDM-eligible); AAPL has well-known EPS figures for Graham |

## Out of Scope

- DCF engine (C2)
- Composite fair value and margin of safety (C3)
- Any persistence (`ValuationResult` is C3)
- Any REST endpoint (C3 adds `POST /api/v1/securities/{symbol}/valuation/dcf`)
- Data mapping from `FundamentalSnapshot` or `RatioSnapshot`
