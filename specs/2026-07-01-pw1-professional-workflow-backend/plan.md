# PW1 Professional Workflow Backend Plan

1. Schema and domain model
   - Add Flyway tables for research snapshots, investment checklists, checklist criteria, checklist evaluations, user competence preferences, and advisor acknowledgement state if not already present.
   - Add JPA entities and repositories following existing package conventions.

2. Research decision audit trail
   - Add a service that creates immutable research snapshots with best-effort platform state for portfolio/watchlist actions.
   - Hook snapshot creation into add/remove portfolio and watchlist workflows.
   - Add `GET /api/v1/audit/decisions?symbol=&from=&to=` with user-owned visibility and admin-wide visibility.

3. Investment checklist framework
   - Add checklist CRUD endpoints under `/api/v1/checklists`.
   - Add `POST /api/v1/checklists/{id}/evaluate/{symbol}` returning persisted pass/fail/no-data results.
   - Support quantitative criteria where matching platform metrics exist and manual criteria as pending/manual-required.

4. Confidence and verification services
   - Add `ValuationConfidenceService.compute(symbol)` with overall level and factor breakdown.
   - Add `DataVerificationService.check(symbol)` with field-level data-quality flags.
   - Include results in available backend review/valuation response paths where practical without breaking existing contracts.

5. Advisor scoping and competence preferences
   - Add advisor acknowledgement read/update support for the current session/user model.
   - Add `GET/PUT /api/v1/preferences/competence`.
   - Add optional competence sector filtering support where existing screener/universe filters can accept it safely.

6. Tests and validation
   - Add focused unit or slice tests for audit immutability, checklist evaluation, confidence scoring, verification flags, and competence preference persistence.
   - Run backend Maven tests for the affected area or full backend test suite if feasible.
