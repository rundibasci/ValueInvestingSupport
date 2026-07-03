# RD2-1 Plan - Agent 1 Curated Universe Walkthrough

1. Create the phase evidence structure.
   - Add `specs/2026-07-03-rd2-curated-demo/`.
   - Add `screenshots/` and `evidence/` subfolders.
   - Document the phase scope, assumptions, and validation criteria.

2. Add the curated-universe replay script.
   - Add `scripts/rd2-agent1-curated-universe-walkthrough.ps1`.
   - Support `-SkipLiveApi` to generate a dry-run manifest without a running backend.
   - In live mode, authenticate admin and investor users, call the universe curation, ingestion, screener, review, comparison, portfolio, watchlist, dashboard, and alert evidence endpoints, and write redacted artifacts.

3. Add stakeholder-facing report scaffolding.
   - Create `walkthrough-report.md` with expected observations, pass/fail slots, RD1 comparison notes, and decision-support boundary language.
   - Create `screenshots/README.md` with route, persona, filename, and redaction expectations for each major screenshot.

4. Validate the evidence pack.
   - Run the RD2 script with `-SkipLiveApi`.
   - Confirm the dry-run manifest is written under the RD2 evidence folder.
   - Confirm all generated spec files are non-empty.
   - Run a focused repository status and diff review.

5. Update external activity and merge.
   - Append the phase activity to the Obsidian vault log.
   - Commit the generated spec files and replay script.
   - Push the phase branch, update the changelog through the merge workflow, merge to `main`, and push `main`.
