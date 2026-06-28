# INGR Review Page Bug Notes

## Scope

- Target route: `/securities/INGR/review`.
- Runtime: Docker Compose stack with PostgreSQL, Redis, backend, and frontend containers.
- User: `admin@localstack.local`.
- Method: exercised the review-page routes and button-backed API calls from the running containers, and verified SPA route targets through nginx.

## Findings

### Fixed - Historical Graphs Only Showed Current Year

- **Severity:** Blocker for review-page assessment.
- **Surface:** In-depth review charts for INGR: financials, earnings, debt, ROIC, ROE, and ratio-based graphs.
- **Observed:** INGR review packet had only one annual financial row and one ratio row, so charts rendered only the current year.
- **Cause:** `SeedService.persistFundamentals` used only FCF history length to decide how many annual rows to persist. INGR had revenue/net-income history but no FCF history, so available historical fundamentals were discarded. Ratios were also persisted only as a single TTM row.
- **Fix:** Seed now persists annual fundamentals using the longest available revenue, net income, or FCF history. It also persists TTM fundamentals and annual ratio points for chart continuity when the provider only returns current ratio values.
- **Verification:** After reseeding INGR, `/api/v1/securities/INGR/financials` returns 2026, 2025, 2024, and 2023. `/api/v1/securities/INGR/ratios` returns 2026 through 2017.

### Fixed - Watchlist Button Allowed Duplicate Add Attempt

- **Severity:** Polish/UX bug.
- **Surface:** Header action `Add to watchlist`.
- **Observed:** INGR was already on the user's watchlist, but the review page still presented an enabled add button. Pressing it returned `409 Conflict`.
- **Fix:** The review page now loads the watchlist, disables the button when the symbol is already monitored, changes the label to `Already on watchlist`, and shows a status message.

### Fixed - Custom DCF Reported Success With Unavailable Fair Value

- **Severity:** Copy/state bug.
- **Surface:** `Run custom DCF` button in the Custom DCF panel.
- **Observed:** For INGR, the backend correctly returned `dcfFairValue: null` because DCF was not eligible, but the UI showed a success-style message with `Fair value: Unavailable`.
- **Fix:** The UI now distinguishes a calculated DCF from an ineligible DCF and shows a clear status: DCF is unavailable and the backend fell back to eligible valuation models.

## Link And Button Check

- `Back to screener` -> `/screener`: SPA route served successfully.
- `Open Security Detail` -> `/securities/INGR`: SPA route and authenticated API target returned successfully.
- `Refresh or seed` -> `/admin/seed`: SPA route served successfully.
- Section jump links (`Sources`, `Valuation`, `Cash generation`, `Earnings`, `Debt`, `Graphs`, `Dividends`, `Quality`, `Risk`): targets exist in the review page source.
- Peer link `KO` -> `/securities/KO/review`: SPA route served successfully.
- `Security Detail`, `Screener`, `Watchlist`, and `Open Portfolio` next-action links: SPA routes served successfully.
- `Add to watchlist`: now guarded when INGR is already present.
- `Run custom DCF`: returns `200`; UI now reports DCF-unavailable state when fair value is null.
- `Add to portfolio`: API returned `201 Created` for adding INGR to the localstack demo portfolio.

## Remaining Data Gaps

- INGR still has no provider description, exchange, dividend history, quick ratio, interest coverage, score, analyst estimates, or FCF history in the current stored data. The page reports these as unavailable/provider data gaps; they are not broken links or failed buttons.
- Financial charts now have a four-year INGR history because the provider returned four years of revenue/net-income history for the current seed path.
- Ratio charts now have a ten-year x-axis for INGR, but values are repeated from current provider ratios because the current market-data abstraction does not expose historical ratio series.
