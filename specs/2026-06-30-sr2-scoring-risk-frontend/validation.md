# SR2 Scoring & Risk Frontend Validation

## Acceptance Checks

1. Review page shows whether the MoS gate capped a score and displays raw/capped score when both are available.
2. Review page shows the applied sector weight profile with profile weights and a short factual reason.
3. Review page shows Piotroski F-Score factor breakdown, Altman Z-Score zone and formula variant, cyclicality classification, and earnings-quality metrics when data is available.
4. Review page displays explicit unavailable states for missing scoring/risk data.
5. Screener rows include Piotroski score and Altman zone columns.
6. Comparison view includes the new scoring/risk indicators where the current comparison data model supports them.
7. Copy remains decision-support oriented and does not recommend buying or selling.

## Test Strategy

Run from the repository root unless noted:

1. `cd frontend; npm run build`
2. `cd frontend; npm test -- --run` if a test script exists.
3. `cd backend; .\mvnw.cmd test` only if backend contracts are changed.

## Manual QA

1. Open a seeded stock review page and inspect the score and risk sections.
2. Verify negative-MoS examples show the score cap state clearly.
3. Verify screener rows remain readable with and without Piotroski/Altman values.
4. Verify responsive layout does not overlap cards or table cells.

## Merge Readiness

1. Spec files exist and are non-empty.
2. Build/tests pass.
3. `git status` contains only SR2 spec, frontend implementation, focused tests, and changelog changes required for merge.

## Known Risks

1. Existing API responses may expose SR1 data under names different from the roadmap wording.
2. Comparison data may not include every SR1 metric yet; unsupported metrics should be visibly unavailable rather than synthesized.
3. Frontend tests may be limited if the current project has no test runner configured.
