# RD1-2 Plan - Agent 1 Full Feature Walkthrough & Screenshots

1. Walkthrough scope and evidence structure
   - Create a stakeholder-readable RD1-2 spec folder with a walkthrough report and screenshot manifest.
   - Cover every roadmap workflow: auth, dashboard, seed/universe, job control, screener, security detail, in-depth review, watchlist, portfolio, alerts, Google sign-in visibility, and account lifecycle.
   - Preserve the decision-support boundary and avoid describing any model portfolio as investable.

2. Deterministic replay support
   - Add a PowerShell walkthrough runner that exercises the real-demo backend through authenticated API calls.
   - Use the real-demo accounts from `scripts/real-demo-guide.md`.
   - Write replay artifacts under the RD1-2 spec directory so results can be reviewed without parsing console output.

3. Screenshot capture checklist
   - Add a manifest listing the required screenshots, route, persona, expected state, and redaction notes.
   - Use stable file names under `specs/2026-07-02-rd1-agent-1-full-feature-walkthrough/screenshots/`.
   - Document that screenshots must not expose secrets, tokens, raw provider payloads, or personal data.

4. Validation
   - Confirm the generated spec files are present and non-empty.
   - Run the frontend typecheck/build.
   - Run the backend test suite.
   - Review git status and changed files before merge.
