# HD2 - Demo Polish Pass Validation

## Functional acceptance

- The full-demo/localstack path builds and starts from the Windows development checkout.
- The backend Docker image handles `backend/mvnw` reliably in the Linux build stage.
- The documented local demo flow includes startup commands, demo URLs, seeded credentials, deterministic data assumptions, known limitations, and a stakeholder walkthrough checklist.
- The full demo can run without live FMP/Yahoo calls or committed secrets.
- Repeated INGR reseeding produces no duplicate current-year fundamentals, no duplicate current-date ratios, and no retained stale current ratio rows in review data.
- Review-page charts do not show duplicate current-year/current-date labels after repeated reseeding.
- Review-page percentage metrics render correctly:
  - Dividend yield, payout ratio, ROE, ROIC, margins, and debt ratios display as human percentages when backend values are decimal ratios.
  - Margin of safety and other already-percent values remain correctly scaled.
- `Add to watchlist` transitions to a stable post-success state and cannot produce a duplicate `409` from an immediately repeated click.
- `Add to portfolio` shows a stable success or existing-holding state after refetch, without contradictory messages.
- Recharts container sizing warnings are removed or explained with evidence that charts remain visible and stable.
- Fair value, margin of safety, recommendation, and score surfaces keep decision-support framing and MiFID II disclaimers.

## Automated checks

- Frontend typecheck passes where configured.
- Frontend production build passes.
- Frontend tests and linting pass where configured, or unsupported commands are documented.
- Backend compile passes where supported.
- Targeted backend tests for seed/review idempotency pass where added or already configured.
- Docker build/start checks for the full-demo stack pass, or any environment blocker is documented with exact commands and errors.
- `git diff --check` passes.

## Manual review

- A stakeholder can follow the documented local demo flow without command-line knowledge after the server is running.
- The React app visibly includes the Seed Universe workflow and represents its backend behavior accurately.
- Primary surfaces feel like one product: consistent spacing, labels, actions, badges, disclaimers, loading states, and error handling.
- The INGR review page is checked after repeated reseeding in desktop and narrow/mobile viewports.
- Charts remain visible and legible across desktop and mobile layouts.
- Text, controls, metric cards, badges, and tables do not overlap, clip, or wrap incoherently.
- Empty, loading, partial-data, unavailable-data, unauthorized, expired-session, and API-error states use clear non-technical language.
- No provider secrets, JWT refresh tokens, unintended credentials, stack traces, or sensitive user data appear in source, fixtures, rendered UI, local storage, logs, screenshots, or docs.

## Documentation and merge criteria

- The spec decisions in `requirements.md` are resolved.
- HD2 validation evidence is captured in the implementation notes, changelog, PR notes, or a report under this spec directory.
- Any remaining UX gaps are documented with severity, affected surface, recommendation, and owner/phase.
- Any changes larger than HD2 polish are deferred instead of silently included.
- The branch contains only HD2 spec, implementation, documentation, and validation work plus any unrelated pre-existing untracked logs left untouched.
