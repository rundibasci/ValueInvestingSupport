# HD4 - Beta Feature Selection Requirements

## Phase

Roadmap phase: HD4 - Beta-Driven Feature Selection And Implementation.

Spec directory: `specs/2026-06-28-beta-feature-selection/`.

Branch: `feature/hd4-beta-feature-selection`.

## Source Context

HD4 follows the HD3 beta-tester persona simulation and uses `specs/2026-06-28-hd3-beta-tester-personas/extracted-roadmap-requirements.md` as its initial backlog.

The selected HD4 scope is a focused trust-blocker increment. It implements the highest-value beta findings before Quality & Observability, without expanding into a broad platform redesign.

Project guidance comes from:

- `specs/mission.md`: decision-support boundary, transparency, explainable missing data, portfolio exposure visibility, and research rationale capture.
- `specs/tech-stack.md`: React 18 + TypeScript frontend, Spring Boot 3 backend, shared reference data, user-owned portfolios/watchlists, structured API availability metadata.
- `specs/roadmap.md`: HD4 acceptance checklist and Group I follow-up expectations.

## User Decisions

1. Feature selection: recommended trust blockers.
   - Prioritize score/data-quality transparency.
   - Prioritize portfolio concentration warnings.
   - Prioritize watchlist research rationale.

2. Implementation depth: recommended scoped increment.
   - Build a focused backend/frontend slice that can be implemented and validated before Quality & Observability.
   - Defer lower-priority HD3-derived ideas into named later phases with rationale.

3. Validation focus: recommended demo plus tests.
   - Require targeted automated checks.
   - Require local demo smoke evidence for impacted workflows.
   - Require traceability from HD3 finding to HD4 decision and implementation status.

## Selected Features

### 1. Score And Data-Quality Transparency

Users must understand why a score, valuation, or key provider-backed metric is present, stale, pending, blocked, or unavailable.

Scope:

- Define a shared availability/status vocabulary for score and data-quality states.
- Expose structured status fields through relevant API DTOs where the backend already returns scores, valuations, seed results, review data, screener/search rows, or portfolio holdings.
- Make frontend surfaces show clear status labels instead of blank cells or ambiguous missing values.
- Distinguish at least these states where the data path supports them:
  - `AVAILABLE`
  - `STALE`
  - `PENDING`
  - `PROVIDER_LIMITED`
  - `MISSING_SEEDED_HISTORY`
  - `MISSING_INTERNAL_COMPUTATION`
  - `GUARDRAIL_BLOCKED`

Boundaries:

- Do not introduce live external provider calls from display components.
- Do not require full observability metrics in HD4; Group I remains responsible for broader test and observability hardening.
- Do not convert status labels into investment advice.

### 2. Portfolio Concentration Warnings

Portfolio workflows must surface concentration exposure before and after adding holdings.

Scope:

- Add computed concentration signals where portfolio holding weights and sector weights can be derived from persisted holdings, current prices, and sector data.
- Show warnings in portfolio detail views and add-to-portfolio flows when a holding or sector dominates the model portfolio.
- Keep warning copy factual and decision-support oriented.
- Preserve user ownership boundaries: portfolios and holdings remain user-owned; security metadata remains shared reference data.

Boundaries:

- Do not recommend buy, sell, or order actions.
- Do not require portfolio accounting or P&L tracking.
- If prices or sectors are missing, show an explainable unavailable state rather than suppressing the warning area.

### 3. Watchlist Research Rationale

Watchlist items must capture why a user is monitoring a symbol and what would change their view.

Scope:

- Add a concise user-authored note field for watchlist items.
- Add a monitoring reason/category suitable for HD3 workflows, including:
  - `WAIT_FOR_BETTER_PRICE`
  - `VALUATION_CONCERN`
  - `DATA_QUALITY_GAP`
  - `DIVIDEND_CONCERN`
  - `NARRATIVE_CATALYST`
  - `OTHER`
- Update watchlist create/edit/read flows and UI to display rationale alongside symbol data.
- Ensure rationale data is user-owned and not platform-wide reference data.

Boundaries:

- Do not ingest live news by default.
- Do not turn notes into recommendations.
- Keep fields concise enough for the MVP watchlist workflow.

## Deferred HD3 Requirements

The following HD3-derived items are valuable but deferred from this scoped HD4 increment:

- Screener empty-state diagnostics: defer to Group I or a follow-up screener polish phase because it needs filter analysis across the local result set.
- Cross-symbol comparison: defer to a later research workflow phase because it introduces a new comparison surface.
- Story-versus-fundamentals review support: defer to a narrative research feature phase using curated summaries or saved notes.
- Persona replay scripts: defer to Group I test coverage and regression scripting, while HD4 validation still smoke-tests impacted persona paths.

## Success Criteria

HD4 succeeds when:

- Each selected trust-blocker feature has a documented decision, implementation path, and validation evidence.
- API and UI behavior make missing score/data states explainable.
- Portfolio users see concentration context without receiving personalized investment advice.
- Watchlist users can save and revisit research rationale.
- Every HD3 extracted requirement is marked implemented, deferred to a named phase, or rejected with rationale.
- The local demo confirms impacted HD3 persona workflows remain usable.
