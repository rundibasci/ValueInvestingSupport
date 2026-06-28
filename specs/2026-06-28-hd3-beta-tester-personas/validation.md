# HD3 - Beta Tester Persona Simulation Validation

## Functional acceptance

- The full local demo stack is run for the phase, or any runtime blocker is documented with exact commands, errors, and fallback evidence.
- Backend health, frontend availability, login, seed universe, screener/search, security detail, in-depth review, watchlist, portfolio, rebalancing, dashboard, and alerts are checked at least to the extent needed by the persona workflows.
- All three personas complete a realistic workflow:
  - Candidate discovery.
  - Optional seed or refresh actions.
  - Security research through detail and review pages.
  - Portfolio construction or update.
  - Watchlist construction or update.
  - Documented impressions and recommendations.
- Persona seed actions are documented and reproducible, including ticker lists, seed packs, failed rows, unavailable rows, and handoffs to research pages.
- Reports avoid buy/sell instructions and present portfolio/watchlist outputs as beta-test artifacts, not personalized investment advice.

## Report acceptance

- The spec directory contains a report for each persona:
  - Very prudent value investor.
  - Hedge-fund asset allocator.
  - Financial journalist / trend observer.
- Each report includes:
  - Persona assumptions.
  - Source summaries used.
  - Candidate-stock selection rationale.
  - Seed actions performed.
  - Final portfolio with holdings, weights or quantities, valuation context, and key risks.
  - Watchlist with monitoring rationale, target signals, and exclusion rationale.
  - Platform impressions on usability, trust, data gaps, review pages, portfolio workflow, and watchlist workflow.
  - Prioritized improvement recommendations.
  - Validation evidence and limitations.
- A combined findings index exists and groups recommendations as:
  - Blockers.
  - Product gaps.
  - UX polish.
  - Data-quality concerns.
  - Nice-to-have enhancements.
- The personas surface distinct product needs rather than repeating the same value-investor workflow.
- Any new bugs or UX issues discovered include severity, affected route or surface, observed behavior, expected behavior, reproduction notes, and recommended owner/phase.

## Automated checks

- Frontend typecheck passes where configured if any frontend or route-level fixtures change.
- Frontend production build passes where configured if any frontend change is made.
- Backend compile/tests pass where supported if any backend, fixture, or seed behavior changes.
- `git diff --check` passes.

## Manual review

- Persona workflows use deterministic localstack/full-demo data as the baseline and do not require live provider keys.
- Curated source summaries are included or referenced clearly enough to reproduce candidate selection.
- Seeded securities remain framed as shared research data, while watchlists and portfolios remain user-owned.
- Portfolio and watchlist outputs are internally consistent with the persona rationale.
- Decision-support disclaimers and data-availability labels are considered in persona trust assessments.
- No provider secrets, JWT refresh tokens, restricted raw provider payloads, stack traces, or sensitive user data are captured in reports.
- Any screenshots or API snippets are checked for sensitive data before commit.

## Merge criteria

- The spec decisions in `requirements.md` are resolved.
- All three persona reports and the combined findings index are complete.
- Full local demo run evidence is captured or a blocker is documented.
- Any follow-up bugs or product recommendations are actionable enough to feed HD4.
- Any services started during validation are stopped.
- The branch contains only HD3 spec, reports, fixtures/evidence, and any tightly scoped supporting changes.
