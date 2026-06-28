# HD2 - Demo Polish Pass Plan

1. **Baseline and reproduction**
   - Review HD1 assessment outputs, `ingr-review-bug-notes.md`, local demo docs, Dockerfiles, Compose files, frontend routes, and relevant backend seed/review services.
   - Start from the current branch state and record existing untracked logs or unrelated workspace files without modifying them.
   - Reproduce or inspect the open HD2 issues: Docker build portability, duplicate INGR rows after reseeding, percentage scaling, watchlist duplicate state, portfolio add contradiction, and chart container warnings.
   - Capture baseline commands and evidence so fixes can be compared against observed behavior.

2. **Docker and local demo readiness**
   - Verify the backend Docker build path works from a Windows checkout, including Maven wrapper line-ending normalization and executable permissions.
   - Review startup documentation for the full-demo/localstack flow and update it with exact commands, demo URLs, seeded credentials, deterministic data expectations, and known limitations.
   - Confirm the documented flow does not require live FMP/Yahoo calls, `.env` secrets, or manually copied credentials beyond intended demo setup.
   - Add or update a short stakeholder walkthrough checklist for login, seed universe, screener, review page, watchlist, portfolio, rebalancing, dashboard, and alerts.

3. **Review data idempotency**
   - Inspect the backend seed persistence path for fundamentals, ratios, quotes, valuations, and review-packet data used by `/securities/{symbol}/review`.
   - Make reseeding idempotent for current/TTM rows and same fiscal-period rows while preserving immutable historical snapshots where required by the domain model.
   - Ensure repeated INGR reseeding does not produce duplicate current-year fundamentals, duplicate current-date ratios, or retained stale current ratio rows.
   - Add targeted automated coverage or service-level checks for duplicate prevention where the repository patterns support it.

4. **Review-page UI polish fixes**
   - Normalize percentage formatting with field-aware helpers so decimal ratios and already-percent values display correctly.
   - Update watchlist mutation success handling to refresh or optimistically update query state and immediately guard against duplicate adds.
   - Update portfolio-add state handling so success, existing-holding, and loading/error states do not appear contradictorily after refetch.
   - Stabilize review-page chart containers to avoid Recharts `width(-1)` / `height(-1)` warnings while preserving responsive desktop and mobile layouts.
   - Keep copy concise, operational, and aligned with decision-support disclaimers.

5. **Full demo walkthrough**
   - Run the deterministic full-demo stack and React frontend.
   - Walk the stakeholder path: login, dashboard, seed universe, screener, security detail, in-depth review, watchlist, portfolio builder, rebalancing, and alerts.
   - Re-test the INGR review route before and after repeated reseeding, including chart labels, metric cards, watchlist add, custom DCF state, and portfolio add.
   - Check desktop and narrow/mobile viewports for chart visibility, text overlap, clipped controls, and stable action states.

6. **Automated verification and documentation**
   - Run frontend typecheck, build, and tests/lint where configured.
   - Run backend compile and targeted tests where supported by the environment.
   - Run Docker build/start checks needed for the full-demo flow.
   - Run `git diff --check`.
   - Document validation evidence, remaining UX gaps, known limitations, and any deferred roadmap recommendations with severity and suggested owner/phase.
