# VM2 - Valuation Transparency Frontend Requirements

## Scope

- Extend the React in-depth review page so valuation outputs are transparent enough for a conservative value-investing workflow.
- Display DCF terminal-value dependence, WACC inputs, EPV conservative floor, owner earnings, Graham checklist results, and composite-weight controls.
- Surface the VM1 backend outputs through the review API when they are already persisted but not yet exposed.
- Keep all copy decision-support oriented and preserve the MiFID II advice boundary.

## Exclusions

- Do not add persistent user preference APIs for composite weights in this phase.
- Do not change the core valuation formulas introduced in VM1.
- Do not add broker/order execution, buy/sell recommendations, or personalized advice language.
- Do not replace existing security-detail or custom DCF flows outside the review page.

## Decisions

- The first unstarted roadmap phase after VM1 is VM2, so this phase implements `Phase VM2: Valuation Transparency Frontend`.
- The review page is the primary implementation surface because the roadmap requires the transparency controls on the review page and valuation tab, and the current frontend already has a complete review packet route.
- Backend changes are limited to DTO exposure and read-only composition so the frontend can render existing VM1 persisted data.
- Composite-weight controls recompute the displayed composite locally for research comparison; persistence is deferred because the roadmap says per-user preferences were intentionally not exposed during VM1.

## Assumptions

- The local date for this phase is 2026-06-30.
- Persisted VM1 data may be absent for symbols valued before VM1; the UI must show clear unavailable states instead of failing.
- A persisted sensitivity matrix is not yet stored, so the review endpoint may derive a deterministic matrix from current FCF, shares, net debt, and latest WACC using conservative default growth assumptions.
- Current frontend validation is TypeScript build/typecheck plus backend Maven tests for changed DTO/service behavior.

## Dependencies

- VM1 backend fields on `valuation_result`, `wacc_result`, and `graham_checklist_item`.
- React 18, TypeScript, TailwindCSS, TanStack Query, and Recharts in `frontend`.
- Spring Boot review endpoint `GET /api/v1/securities/{symbol}/review`.

## Context

- Mission principles require transparency, conservative defaults, missing-data explanations, and MiFID II disclaimers on fair-value screens.
- VM2 roadmap acceptance requires WACC inputs, terminal-value warning, EPV, owner earnings, Graham checklist, DCF sensitivity, and composite-weight controls to be visible and understandable.
