# Plan - Phase RCL1: Screener And Symbol Recycling Pass

1. Inspect screener and comparison contracts.
   - Read backend screener request/response DTOs, controller, service, and validation paths.
   - Read conservative workflow/Agent 1 comparison backend contracts.
   - Read frontend screener page and API client code.
   - Identify the current numeric threshold convention and where `{}` or fractional values fail.

2. Harden screener API validation.
   - Add request normalization or validation for empty, missing, fractional, and percentage threshold payloads.
   - Ensure invalid inputs return `400` with actionable field errors instead of `500`.
   - Add focused backend tests for empty request, UI-standard request, fractional request, and malformed numeric request.

3. Fix screener UI empty state and landmark structure.
   - Correct `companyies` copy.
   - Make empty-state copy distinguish screener results from Agent 1 comparison candidates.
   - Remove duplicate `main` landmarks on the screener route without breaking the shared shell.
   - Add focused frontend test or lightweight verification for one primary main region where practical.

4. Canonicalize Berkshire class B symbol handling.
   - Locate symbol normalization points in seeding, security lookup, watchlist, portfolio holdings, and comparison logic.
   - Add or reuse a symbol alias helper so `BRK.B` and `BRK-B` resolve consistently.
   - Ensure display copy can preserve a user-friendly symbol while backend lookup uses the canonical provider symbol.
   - Add backend tests covering securities lookup, review lookup, and portfolio enrichment for both alias forms.

5. Capture log-correlation and replay evidence.
   - Reproduce fixed screener payloads against the local API and record status codes.
   - Check Docker/backend logs around the validation run for absence of unexpected `5xx`.
   - Document evidence in `validation.md`.

6. Validate and prepare merge.
   - Run focused backend tests.
   - Run frontend typecheck/build or the smallest meaningful frontend validation for touched code.
   - Review `git diff --stat` and ensure unrelated beta artifacts/logs are not staged.
   - Update the Obsidian activity note with implementation summary, validation, and remaining RCL follow-ups.
