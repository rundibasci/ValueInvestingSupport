# PW2 Professional Workflow Frontend Validation

## Acceptance Checks

- `/audit` displays decision snapshots with symbol, action, timestamp, price, fair value, margin of safety, score, source, and rationale when returned by the API.
- Decision history can be exported to CSV from the client.
- Checklist management supports creating, editing, deleting, and evaluating a checklist for a selected symbol.
- Security review shows confidence level, confidence factors, verification warnings, and checklist evaluation summary.
- Portfolio workflows show ADVISOR scope copy for ADVISOR users with an acknowledgement action.
- Account/settings surface allows editing competence sectors and review/screener surfaces indicate outside-competence symbols.

## Test Strategy

- Run `npm run typecheck` in `frontend`.
- Run `npm run build` in `frontend`.
- If backend contracts are touched, run the affected Maven tests; otherwise frontend validation is sufficient.

## Manual QA

- Review new routes and existing portfolio/review/screener/account pages for missing-data rendering and non-advisory language.
- Confirm no secrets or user-specific credentials are committed.

## Merge Readiness

- Spec files exist and are non-empty.
- Validation commands pass.
- `git status` contains only PW2 spec, frontend implementation, validation evidence if generated, changelog, and optional external vault updates.

## Known Risks

- Backend DTO names may differ from inferred frontend types; use tolerant optional fields and centralize normalization in the API client.
- Some competence filtering may remain display-only if the screener request contract does not already support it.
