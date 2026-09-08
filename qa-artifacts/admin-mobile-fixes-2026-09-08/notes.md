# ADMIN-role mobile/accessibility fix pack — 8 settembre 2026

Fixes every item from `qa-artifacts/admin-responsive-accessibility-2026-09-08/report.md`
(Problemi e priorità table), applied without a manual guided walkthrough per the user's
request — verified via Playwright against the real ADMIN account (`admin@localstack.local`)
with its real seeded/populated data, matching what the audit itself tested.

## Fixes

1. **Dashboard "Top movers" page overflow (Alta)** — `DashboardPage.tsx`'s `Panel`
   component (used 4x on the page) was missing `min-w-0`; below the `xl:` breakpoint its
   parent grid has no explicit columns, so the "Top movers" table's `min-w-[38rem]`
   inflated the whole panel instead of scrolling inside its own `overflow-x-auto`.
   Added `min-w-0` to `Panel`'s root `<section>`.
2. **Data Fallbacks table, 1250px in 348px (Alta)** — `MarketDataFallbacksPage.tsx`:
   added an `EventCard` mobile list (`lg:hidden`) alongside the existing table
   (`hidden lg:block`), same split already used by Portfolio's holdings tables.
3. **Admin Jobs table, 1100px, actions ~750px off-screen (Media)** — `AdminJobsPage.tsx`:
   added a `JobCard` mobile list with all four actions (Run now/Enable-Disable/History/
   Events) immediately visible instead of at the end of a wide row; added `min-h-11` to
   every action button in both the card and table renderings (was 34px, below the
   44px touch-target guideline).
4. **Universe Curation preview table, 820px in 348px (Media)** — `UniverseCurationPage.tsx`
   `PreviewTable`: added a mobile card list. Also converted the sibling `SeedResultsTable`
   (900px, not runtime-tested by the audit but the same pattern/risk) for consistency.
5. **Users table, 760px in 348px (Media)** — `UserProvisioningPage.tsx`: added a mobile
   card list with the Enable/Disable action (`min-h-11`).
6. **`MultiSelect` duplicate accessible name (Media)** — `UniverseCurationPage.tsx`: the
   free-text fallback input shared its accessible name with the `<select multiple>` it
   sits under (both nested in the same `<label>`). Gave the input its own
   `aria-label="<Field>, comma-separated list"` (which overrides the inherited label per
   the accessible-name computation) plus a visible hint line ("Or edit as a
   comma-separated list") so sighted users also understand the second control's purpose.

## Verification
- All fixes tested against real ADMIN data: the Dashboard portfolio with real KO/MSFT/JNJ
  holdings (the exact populated state that produced the original 280px overflow), a real
  Universe Curation preview (100 of 1431 matching symbols, real FMP market data), the real
  Data Fallbacks event log (24 events), Admin Jobs (7 scheduled jobs), and Users (5 real
  accounts).
- `document.documentElement.clientWidth === scrollWidth` at 390px on every fixed page
  (was up to 670 on Dashboard).
- Desktop (1440px): `getComputedStyle` confirms the mobile card list is `display: none`
  and the original table is visible at `lg:` and above on every converted page — no
  regression, screenshots included.
- `aria-label` on the `MultiSelect` free-text inputs verified distinct from the `<select>`'s
  inherited label via `getAttribute('aria-label')`.
- Action-button heights measured 44px (`min-h-11`) on Admin Jobs cards, up from 34px.
- `npm run typecheck` and `npm run build` clean.

## Screenshots
`dashboard-top-movers-fixed-390.png`, `data-fallbacks-cards-390-full.png` /
`-card-detail-390.png`, `admin-jobs-cards-390.png` / `-table-1440-unchanged.png`,
`universe-preview-cards-390.png`, `users-cards-390.png`.
