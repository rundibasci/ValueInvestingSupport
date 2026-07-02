# L4 Conservative Workflow Enhancements - Validation

## Acceptance Checks

- Conservative preset criteria include positive MoS, score availability, dividend coverage, leverage/liquidity resilience, and data completeness.
- Empty-state diagnostics name likely eliminating criteria and suggested relaxations while preserving current criteria.
- Selected-symbol comparison covers MoS, value score, quality, leverage/liquidity, growth, dividend indicators, and source/data coverage.
- Agent 1 journal findings are represented by implemented features, deterministic replay evidence, or explicit deferred follow-ups.
- The 10-stock validation workflow does not present the model as personalized investment advice.
- Every availability status remains covered by deterministic examples or documented L3 evidence.

## Test Strategy

- Backend: focused unit/MVC tests for conservative workflow metadata and comparison APIs.
- Frontend: typecheck and production build; add component-level tests only if a matching local harness already exists.

## Commands

- `cd backend; .\mvnw test -Dtest=ConservativeWorkflowServiceTest,ConservativeWorkflowControllerTest`
- `cd frontend; npm run typecheck`
- `cd frontend; npm run build`

## Manual QA

- Inspect screener preset and empty-state copy for decision-support language.
- Inspect selected-symbol comparison field labels for the Agent 1 symbol set.
- Confirm watchlist rationale categories still support "wait for better price" and data-quality-gap usage.

## Merge Readiness

- Worktree contains only L4 spec files, implementation files, tests, changelog, and optional vault activity note.
- Existing untracked runtime logs remain uncommitted.

## Known Risks

- Some conservative criteria may not yet map to a fully live screener filter; L4 exposes deterministic metadata and diagnostics so the workflow is visible and testable while live filtering can deepen later.

## Validation Evidence

- `cd backend; .\mvnw test "-Dtest=ConservativeWorkflowServiceTest,ConservativeWorkflowControllerTest,ScreenerControllerTest"` - passed on 2026-07-02.
- `cd frontend; npm run typecheck` - passed on 2026-07-02.
- `cd frontend; npm run build` - passed on 2026-07-02; Vite reported the existing large chunk warning for the bundled app.
