# DL4 — Guided Portfolio Simulation Preconditions

## Context

The Portfolio page currently enables simulation and simulation-based rebalance when a portfolio exists and the numeric form is locally valid. The backend remains authoritative, but failures such as `No eligible watchlist candidates` arrive only after submission as generic HTTP errors. Users cannot tell whether they need to populate the watchlist, refresh missing market data, edit holdings, or reconsider an over-restrictive constraint.

This phase implements roadmap phase **DL4: Guided Portfolio Simulation Preconditions** in accordance with `specs/mission.md` and `specs/tech-stack.md`. The feature is decision support: it explains data readiness and constraint effects without recommending securities or weakening the user's value-investing criteria.

## Scope

### Backend

- Add an ownership-safe, read-only precondition endpoint for a selected portfolio and the current simulation input.
- Reuse the candidate eligibility rules already owned by `PortfolioSimulationService`; do not maintain a second simulation engine or a divergent set of filters.
- Return a structured diagnostic response containing:
  - whether simulation can currently be submitted;
  - whether simulation-based rebalance can currently be submitted;
  - stable reason codes and user-safe messages;
  - watchlist totals, eligible-candidate count, and exclusion counts grouped by the existing exclusion reasons;
  - portfolio holding totals and priced/unpriced holding counts relevant to rebalance readiness;
  - recovery-action identifiers suitable for frontend routing.
- Distinguish at least these states where supported by current domain data:
  - no watchlist items;
  - watchlist items exist but none have usable prices;
  - candidates are excluded by missing score, valuation, sector, country, or required yield data;
  - the submitted constraints eliminate every otherwise data-ready candidate;
  - the budget cannot buy a share within the caps;
  - empty portfolio;
  - portfolio contains unpriced holdings;
  - eligible candidates exist.
- Preserve structured diagnostics on authoritative `422 Unprocessable Entity` responses so a race between preflight and command execution remains understandable.
- Preserve existing authentication, portfolio ownership, validation, transaction, disclaimer, and HTTP semantics.

### Frontend

- Query preconditions for the active portfolio and current simulation form using TanStack Query, with a small debounce or explicit stable query key to avoid excessive requests while typing.
- Present concise readiness guidance near the simulation controls, separating platform facts from suggested recovery actions.
- Disable a command only when the backend has established that it is impossible with the current data and constraints. Loading, stale, or failed diagnostics must not masquerade as an ineligible result.
- Explain every disabled command and provide relevant navigation or focus actions:
  - watchlist;
  - screener;
  - portfolio holdings editor;
  - simulation constraints.
- Continue displaying structured `422` diagnostics returned by simulation or rebalance commands.
- Invalidate or refetch preconditions after watchlist changes, holding changes, portfolio selection, and successful relevant data mutations.
- Keep all existing decision-support disclaimers visible and do not introduce buy/sell recommendations.

## Decisions

1. **Backend is the source of truth.** React may validate input shape and render diagnostics, but it must not reproduce eligibility logic.
2. **One shared eligibility evaluator.** Extract or reuse a backend evaluation component that both preflight and `simulate` consume, preventing preflight/submit drift.
3. **Request-aware preflight.** Preconditions must evaluate the user's current budget, margin-of-safety, yield, stock, sector, and country limits; a static portfolio-only endpoint cannot identify constraint elimination.
4. **Stable machine codes, readable messages.** UI behavior depends on documented codes, never string parsing of exception text.
5. **No automatic constraint relaxation.** Recovery guidance may point to the constraints, but the user decides whether to change them.
6. **Conservative disabling.** Only deterministic impossibility disables an action. Unknown availability remains actionable and command errors remain authoritative.
7. **Simulation and rebalance are separate capabilities.** The response explains each command independently because holdings readiness can affect rebalance without changing candidate eligibility.
8. **No new persistence is required.** Preconditions are derived from current watchlist, holdings, quotes, scores, valuations, ratios, and request values.

## Out of Scope

- Changing allocation weights, ranking, caps, or valuation calculations.
- Automatically adding watchlist entries or holdings.
- Automatically relaxing margin-of-safety, dividend-yield, diversification, or other constraints.
- Price refresh orchestration, market-data fallback changes, or background-job progress.
- Personalized investment advice, expected-return promises, or trade execution.
- A redesign of the entire Portfolio page.

## Compatibility and Risks

- Existing clients of `POST /simulate` and `POST /rebalance` must remain compatible.
- Candidate evaluation refactoring risks changing allocation results; regression tests must pin existing successful and exclusion behavior.
- Diagnostics can become stale between preflight and submission; commands therefore repeat validation and return the same structured reason family.
- Counts must not expose another user's watchlist or portfolio data.
- Response messages must not imply that data completeness makes an investment suitable.

