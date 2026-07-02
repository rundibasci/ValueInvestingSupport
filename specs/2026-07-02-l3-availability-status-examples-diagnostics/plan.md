# L3 Availability Status Examples And Diagnostics - Plan

1. Backend diagnostics model
   - Add a small diagnostics API that returns deterministic examples for every shared availability state.
   - Include affected surfaces, conservative interpretation text, and decision-support notes for each state.
   - Keep examples independent of live market-data providers.

2. Backend tests
   - Add focused tests proving all `AvailabilityStatus` enum values have exactly one diagnostic example.
   - Verify examples contain non-empty reasons, interpretation text, and affected surfaces.
   - Verify the endpoint is authenticated and serializes status values predictably.

3. Frontend rendering
   - Centralize status display metadata for all shared availability states.
   - Update review/screener availability badges to cover `PENDING`, `PROVIDER_LIMITED`, `MISSING_SEEDED_HISTORY`, `MISSING_INTERNAL_COMPUTATION`, and `GUARDRAIL_BLOCKED` consistently.
   - Add a diagnostics section to the review page that fetches deterministic examples and links them to the current availability badges.

4. Frontend tests and validation
   - Add focused rendering tests where the current frontend test harness supports them, or validate with typecheck/build if no component test pattern exists.
   - Run backend focused tests plus the smallest meaningful frontend validation command.
   - Record validation evidence in this spec and the Obsidian activity note.

5. Documentation and merge
   - Update this spec if implementation constraints require scope changes.
   - Update the Obsidian activity log.
   - Commit, push, update changelog, merge into `main`, and push `main` when validation passes.
