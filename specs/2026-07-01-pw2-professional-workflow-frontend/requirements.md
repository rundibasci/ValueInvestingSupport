# PW2 Professional Workflow Frontend Requirements

## Scope

- Implement Phase PW2 from `specs/roadmap.md`.
- Connect React frontend surfaces to the PW1 professional workflow backend:
  - research decision timeline and CSV export,
  - investment checklist builder and stock evaluation,
  - confidence badge and factor breakdown,
  - data verification warnings,
  - ADVISOR scope acknowledgement,
  - circle-of-competence preferences and indicators.
- Preserve decision-support language and avoid buy/sell recommendations.

## Exclusions

- No backend schema or endpoint changes unless required to fix frontend integration issues.
- No new external services.
- No regulated suitability, client-risk-profiling, brokerage, or execution workflows.

## Decisions

- The React app is the delivery surface; no static HTML demo page is required for this phase.
- Missing backend values are rendered as unavailable/pending, not as zero or pass/fail.
- CSV export is generated client-side from the displayed decision history.
- ADVISOR acknowledgement is stored through the PW1 backend when available and also guarded by session state to avoid repeated interruption.

## Assumptions

- PW1 backend endpoints exist under `/api/v1/audit/decisions`, `/api/v1/checklists`, `/api/v1/professional/insights/{symbol}`, `/api/v1/preferences/competence`, and the advisor acknowledgement endpoint implemented in PW1.
- Existing auth context exposes the current user's role or token-derived role sufficiently for role-aware UI.
- The current screener API can accept competence filtering only if already supported; otherwise PW2 displays competence indicators without expanding the request contract.
- The earliest unstarted roadmap phase is PW2 because specs and merge history show PW1 complete and no PW2 spec exists.

## Dependencies

- React 18, TypeScript, Vite, TailwindCSS.
- Existing frontend API client and auth provider.
- PW1 backend DTOs and security rules.

## Context

- Mission principles require transparent valuation, explainable missing data, decision-support language, and advisor-scope clarity.
- Professional workflow features must make research state traceable without implying investment advice.
