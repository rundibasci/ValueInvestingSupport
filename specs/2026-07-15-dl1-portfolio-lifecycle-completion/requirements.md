# Requirements — DL1: Portfolio Lifecycle Completion

## Purpose

Complete the user-owned portfolio lifecycle with a safe product-level deletion flow. The real-demo walkthrough proved that portfolios can be created and holdings can be removed, but a temporary portfolio still requires direct PostgreSQL cleanup. DL1 removes that operational gap without broadening portfolio accounting or administration scope.

This phase supports the mission's portfolio-construction cycle and preserves the ownership, decision-support, and immutable research-history boundaries defined in `specs/mission.md`.

## Scope

- Add `DELETE /api/v1/portfolios/{id}` for authenticated `INVESTOR`, `ADVISOR`, and `ADMIN` users acting on their own portfolios.
- Resolve the authenticated user and portfolio through the existing ownership-safe `findByIdAndUser` path.
- Return `204 No Content` after a successful deletion.
- Delete dependent holdings, rebalance proposals/lines, and analytics snapshots using existing entity/database cascade behavior.
- Preserve append-only research-decision audit records and all platform-wide security, market-data, valuation, and score history.
- Add a destructive action to the React portfolio page with explicit confirmation containing the portfolio name.
- Refresh portfolio-dependent frontend state after success and select another portfolio or show the existing empty state.
- Cover backend ownership/cascade behavior and frontend confirmation, success, error, and last-portfolio behavior.

## Existing Context

- `PortfolioController` already exposes list, create, detail, analytics, simulation, rebalance, and holding mutations under `/api/v1/portfolios`.
- `PortfolioService.resolvePortfolio(UUID, User)` already hides cross-user resources behind `404 Not Found`.
- `PortfolioRepository` already provides `findByIdAndUser(UUID, User)` and `JpaRepository.delete`.
- `Portfolio.holdings` is configured with `cascade = ALL` and `orphanRemoval = true`.
- The database foreign keys for holdings, rebalance proposals/lines, and portfolio analytics snapshots use `ON DELETE CASCADE`.
- The frontend already uses TanStack Query for portfolio list/detail/analytics state and already has a holding-delete mutation pattern.
- The walkthrough in `doc/2026-07-15-real-demo-admin-value-analyst-walkthrough.md` documents direct database cleanup as the current workaround.

## Decisions

### Hard delete is intentional for the portfolio container

DL1 deletes the user-owned portfolio container and its portfolio-scoped operational children. It does not delete immutable research decisions or shared research data. A soft-delete column and archived-portfolio UI would add lifecycle states that are not required by the roadmap finding.

### Ownership failures remain indistinguishable

An unknown portfolio and another user's portfolio both return `404`. `ADMIN` does not gain cross-user deletion through this endpoint; administrative lifecycle tooling is outside DL1.

### Confirmation is required but name re-entry is not

The frontend must display a confirmation that names the selected portfolio and states that holdings, analytics snapshots, and saved rebalance proposals will be removed. A second typed-name challenge is unnecessary for this bounded MVP action.

### No response body after deletion

The endpoint returns `204 No Content`. The frontend already has the portfolio identity and does not need a deleted-resource representation.

### Audit history remains append-only

Existing research-decision audit records are retained even if their originating portfolio no longer exists. DL1 must not add cascading foreign keys from those records to portfolios or alter the immutable audit policy.

### Frontend selection is deterministic

After deletion, invalidate the portfolio list and all queries keyed by the deleted portfolio. Select the first remaining portfolio returned by the refreshed list; if none remains, clear the selection and show the existing empty-portfolio state.

## Guardrails

- Do not expose whether a portfolio belongs to another user.
- Do not allow unauthenticated deletion.
- Do not delete users, watchlists, checklists, preferences, research audits, securities, fundamentals, quotes, valuations, or scores.
- Do not introduce brokerage/order-execution language or behavior.
- Do not add portfolio P&L/accounting behavior.
- Do not rely only on a frontend confirmation; backend ownership enforcement is authoritative.
- Keep errors actionable without leaking database or ownership details.
- Follow the existing Java 21/Spring Boot/Spring Data JPA and React/TypeScript/TanStack Query stack.

## Out of Scope

- Portfolio archive/restore.
- Bulk portfolio deletion.
- ADMIN deletion of another user's portfolio.
- Undo after the server confirms deletion.
- Deleting or rewriting immutable research-decision audit records.
- Renaming or editing portfolio metadata.
- Export-before-delete workflows.
- Changes to holding, simulation, rebalance, valuation, or market-data calculations.

## Resolved Feature-Spec Questions

- Deletion model: hard delete of the owned portfolio, not archive.
- Authorization: owner-only for every authenticated role, including ADMIN.
- Success contract: `204 No Content`.
- Confirmation: explicit named confirmation, no typed-name requirement.
- Last portfolio: return to the established empty state.
- Audit treatment: retain immutable research history.

