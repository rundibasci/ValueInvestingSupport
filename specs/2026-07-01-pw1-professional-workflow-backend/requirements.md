# PW1 Professional Workflow Backend Requirements

## Scope

Implement the backend foundation for professional workflow and compliance features from the roadmap:

1. Research decision audit trail for portfolio and watchlist actions.
2. Investment checklist CRUD and symbol evaluation.
3. Intrinsic value confidence scoring.
4. Data cross-verification flags.
5. ADVISOR role scoping support through session acknowledgement state.
6. Circle-of-competence preferences for sectors and industries.
7. Flyway migration and focused automated coverage.

## Exclusions

- React UI for timelines, checklist builder, advisor banner, and competence indicators belongs to PW2.
- Export formats for audit history are deferred to PW2 unless backend patterns already expose exports.
- Broker/client suitability, best execution, and regulated investment recommendation workflows remain out of scope.
- External provider reconciliation beyond the persisted platform data is out of scope for this phase.

## Decisions

- Select PW1 because existing specs and branch history show JC2 and PI2 are complete, and PW1 is the earliest unstarted roadmap phase.
- Keep audit snapshots append-only at the application layer: expose reads and creation hooks, but no update/delete API.
- Reuse existing authenticated-user and role patterns rather than introducing a new security abstraction.
- Store checklist criteria as structured rows so quantitative and manual criteria can evolve without JSON-only persistence.
- Compute confidence and verification results from available persisted platform data; return no-data factors instead of failing when historical data is incomplete.
- Persist ADVISOR acknowledgement per session/user state only as far as the current auth model supports without UI changes.

## Assumptions

- Portfolio and watchlist service methods are the correct integration points for automatic audit snapshot creation.
- The existing schema has enough valuation, score, security, quote, fundamental, and ratio fields to populate best-effort audit snapshots.
- If a metric is not present in current entities, audit/confidence/verification fields may be nullable with an explicit availability note.
- Current backend validation should use Maven tests from `backend`.

## Dependencies

- Existing Spring Boot 3 / Java 21 backend.
- Existing Flyway migration chain.
- Existing authentication principal model and `User`/role entities.
- Existing portfolio, watchlist, valuation, score, and security persistence.

## Context

PW1 supports the mission principles of transparency, separation of decision support and advice, missing-data explainability, portfolio exposure visibility before action, and research rationale attached to the workflow.
