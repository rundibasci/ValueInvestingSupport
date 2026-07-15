# DL4 — Implementation Plan

## 1. Contract and Domain Model

1. Define stable precondition status, reason-code, recovery-action, grouped-exclusion, and response DTOs.
2. Define the request-aware endpoint contract using the existing validated `SimulationRequest` fields.
3. Document which reasons block simulation, which block simulation-based rebalance, and which are warnings only.
4. Keep existing command response contracts backward compatible while adding structured `422` error details through the application's standard error mechanism.

## 2. Shared Backend Eligibility Evaluation

1. Refactor candidate discovery and exclusion classification from `PortfolioSimulationService` into a shared, read-only evaluator.
2. Preserve existing ordering, eligibility rules, defaults, ownership checks, and allocation results.
3. Add portfolio holding and price-readiness evaluation needed for rebalance guidance.
4. Derive distinct results for empty watchlist, missing market/fundamental data, constraint elimination, unaffordable allocation, empty holdings, and unpriced holdings.
5. Ensure all repository reads remain scoped to the authenticated user and selected portfolio.

## 3. Preconditions API and Command Errors

1. Add the authenticated portfolio precondition endpoint to `PortfolioController`.
2. Return deterministic eligibility flags, diagnostics, counts, and recovery actions.
3. Make simulation and simulation-based rebalance reuse the evaluator immediately before execution.
4. Replace generic known `422` failures with stable domain codes and structured diagnostics while retaining readable messages.
5. Preserve 400 validation, 401 authentication, ownership-safe 404, and unexpected 5xx behavior.

## 4. Frontend API and Query Integration

1. Add TypeScript types and a client method for the precondition contract.
2. Query by portfolio ID and normalized simulation input using TanStack Query.
3. Prevent request storms while users edit numeric inputs and avoid querying invalid local forms.
4. Invalidate readiness after holding mutations and other relevant cached-data changes.
5. Extend API error parsing so structured `422` diagnostics survive into the UI.

## 5. Guided Portfolio Experience

1. Add a compact readiness panel adjacent to the simulation form.
2. Show separate simulation and rebalance availability with platform facts and plain-language reasons.
3. Disable only commands explicitly marked impossible by a successful current preflight response.
4. Add recovery actions for watchlist, screener, holdings, and constraints without changing constraints automatically.
5. Handle loading, retry, stale-data, API-error, and post-submit race states accessibly.
6. Preserve existing simulation outputs, proposal workflow, and decision-support disclaimer.

## 6. Automated Verification

1. Add backend unit tests for reason classification, grouped counts, recovery actions, and command/preflight parity.
2. Add controller or integration tests for authentication, ownership, validation, successful readiness, and structured `422` responses.
3. Add frontend tests for no watchlist, no priced candidate, over-restrictive constraints, empty portfolio, unpriced portfolio, eligible state, retry, and API errors.
4. Add regression tests proving successful allocation and rebalance outputs remain unchanged.
5. Run backend tests, frontend tests, type checking, linting, and production build per repository tooling.

## 7. Documentation and Merge Readiness

1. Record the endpoint and reason-code contract in the feature spec or API documentation used by the repository.
2. Update `specs/roadmap.md` only after implementation and validation satisfy DL4 acceptance criteria.
3. Complete `validation.md` with commands, evidence, and any known unrelated failures.
4. Review the diff for scope, security, accessibility, disclaimers, and backward compatibility before merge.

