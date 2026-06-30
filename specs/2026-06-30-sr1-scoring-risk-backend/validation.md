# Phase SR1 Validation

## Acceptance Checks

- Negative margin of safety always produces `gateApplied=true` and `totalScore <= 40`.
- `rawTotalScore` records the uncapped score when the gate applies.
- ValueScore responses include the applied sector weight profile.
- Piotroski endpoint returns a 0-9 score and 9 factor details when inputs are available.
- Altman endpoint returns score, zone, formula variant, and components when inputs are available.
- Cyclicality endpoint classifies annual revenue/earnings volatility as stable, moderate, or highly cyclical.
- Earnings quality endpoint reports FCF/net-income ratio, Sloan accruals, classification, and deterioration flag.
- Screener accepts `piotroskiMin`, `piotroskiMax`, and `altmanZone` filters without live provider calls.

## Test Strategy

- Run targeted backend tests for scoring, risk services, and screener filters.
- Run the full backend Maven test suite before merge.

## Commands

- `cd backend; .\mvnw.cmd test`

## Manual QA

- Review generated OpenAPI-like JSON DTO shapes through controller tests.
- Confirm `git diff --stat` only includes SR1 spec, backend implementation, tests, migration, changelog, and directly related configuration.

## Merge Readiness

- Branch pushes cleanly to `origin`.
- Changelog is updated before merging to `main`.
- `main` receives the merge commit and pushes cleanly.

## Known Risks

- Some formulas may have partial data in the current domain model. Missing inputs must result in unavailable/partial result metadata, not misleading numeric output.
- Existing tests may assert exact ValueScore totals; update only where the new RULE-09/profile metadata changes intended behavior.

