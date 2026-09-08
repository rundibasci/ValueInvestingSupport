# Portfolio page full-page horizontal overflow (populated state) — 8 settembre 2026

Found live during the guided mobile walkthrough that followed up
`qa-artifacts/responsive-accessibility-2026-09-08/report.md` (that audit's own limitation:
"Portfolio e watchlist coperti solo negli stati iniziali/vuoti/errore, non con dati
popolati"). Not one of the report's original findings — the empty-state Portfolio page
was already overflow-free.

## Repro
1. Login as INVESTOR, viewport 390x844.
2. Create a portfolio ("Long-term value") from the Portfolio page.
3. As soon as the portfolio is selected, `document.documentElement.scrollWidth` jumps to
   480 against a 390 `clientWidth` — the whole page scrolls horizontally, reproducible on
   a clean reload (not a resize-sequence artifact).

## Root causes (three, compounding)
`PortfolioPage.tsx` has four `grid ... xl:grid-cols-[Xrem_1fr]` / `[1fr_Xrem]` /
`md:grid-cols-[15rem_1fr]` layouts (an arbitrary two-track template, unlike Tailwind's
numeric `grid-cols-N` which already includes `minmax(0,1fr)` and is safe by default).
Below their breakpoint these collapse to an unconstrained implicit single-column grid, so
a descendant's intrinsic minimum width can inflate the grid item, the track, and the page
before any inner `overflow-x-auto` wrapper gets a chance to clip — the same pattern fixed
on the Checklist page in the previous commit, but PortfolioPage had it in four places:

1. Line ~415/419: the "Sector allocation" pie chart's `h-52` wrapper had no `w-full`, so
   its width was indeterminate; Recharts' `ResponsiveContainer` measured `width:0` (the
   `width(-1)/height(-1)` console warning), then rendered at a stale 384x208px fallback
   size instead of tracking its real container.
2. Line ~967 grid (`xl:grid-cols-[20rem_1fr]`, "Your portfolios" sidebar vs. the
   portfolio detail/allocation column): the second grid item had no `min-w-0`.
3. Line ~621 grid (`xl:grid-cols-[1fr_20rem]`, holdings table vs. sector-weights aside):
   the `overflow-x-auto` wrapper around the `min-w-[64rem]` (1024px) holdings table was a
   direct grid item without `min-w-0`.
4. Line ~1378 grid (`xl:grid-cols-[1fr_20rem]`, proposed-allocation table vs. the second
   "Sector allocation" chart in the rebalance-simulation flow): same pattern — the table
   wrapper needed `min-w-0`, and that second chart's `h-56` wrapper needed `w-full` too.
5. `BenchmarkPanel`'s root card (rendered as the third item in a bare `xl:grid-cols-3`
   row, which has no explicit `grid-cols` below `xl` either) contained a
   `min-w-[24rem]` (384px) table in its own `overflow-x-auto` wrapper, but the card
   itself needed `min-w-0` to stay inside its grid track.

## Fix
`frontend/src/pages/PortfolioPage.tsx`:
- `h-52` -> `h-52 min-h-52 min-w-0 w-full` and `h-56` -> `h-56 min-h-56 min-w-0 w-full`
  on both Sector-allocation chart wrappers, matching the `min-w-0 w-full` convention
  already used for every other chart in the codebase (SecurityDetailPage,
  SecurityReviewPage).
- `min-w-0` added to the four grid items identified above.
- Audited every other `grid-cols-[...]` arbitrary-template use across the frontend
  (`pages/*.tsx`, `components/*.tsx`); all others either already use `minmax(0, ...)`
  in the track definition (safe) or have no wide/table descendant (LoginPage,
  AuditPage — left alone). `AppShell.tsx`'s top-level shell grid was already correctly
  guarded (`<div className="min-w-0">` wrapping `<main>`).

## Verification
- `before-390-page-overflow-480px.png`: full-page screenshot at 390px, page rendering
  ~480px wide right after creating a portfolio.
- `after-390-contained-390px.png`: same repro after all five fixes — full-page
  screenshot stays exactly 390px wide (image is 390x4922); the Benchmark comparison
  table clips and scrolls within its own container instead of widening the page.
- `after-1440-desktop-no-regression.png`: desktop viewport, layout unaffected.
- `document.documentElement.{clientWidth,scrollWidth}` measured equal at 390px and
  1440px after the fix (390/390, 1440/1440); no `recharts` `width(-1)/height(-1)`
  console warning changes the rendered chart size any more.
- `npm run typecheck` clean.
