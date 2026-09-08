# Checklist "Evaluate a symbol" full-page horizontal overflow — 8 settembre 2026

Found live during the guided mobile walkthrough that followed up
`qa-artifacts/responsive-accessibility-2026-09-08/report.md` (that audit only exercised
initial/empty checklist states, not a populated evaluate result — a documented limit of
that pass). Not one of the report's original findings.

## Repro
1. Login as INVESTOR, viewport 390x844.
2. Create a checklist with one criterion, then "Evaluate a symbol" (e.g. `KO`).
3. Once the results table renders, the whole page becomes ~702px wide inside a 390px
   viewport (`document.documentElement.scrollWidth` 702 vs `clientWidth` 390) —
   horizontal scroll on the entire page, not just the table.

## Root cause
`ChecklistPage.tsx`'s top-level layout is `<div className="grid gap-6 lg:grid-cols-[18rem_1fr]">`.
The results table is `<table className="w-full min-w-[40rem] ...">` inside a
`<div className="mt-5 overflow-x-auto">`, which is the correct pattern on its own — but
the grid item wrapping the form + evaluate section had no `min-w-0`. A CSS grid/flex
item's default `min-width: auto` lets a descendant's intrinsic minimum size (here, the
table's `min-w-[40rem]` = 640px) inflate the item's own minimum width, which inflates the
grid track, which inflates the whole page — the `overflow-x-auto` wrapper never gets a
chance to clip and scroll internally because its own ancestor already grew to fit it.

## Fix
`frontend/src/pages/ChecklistPage.tsx`: added `min-w-0` to the grid item at line 88
(`<div className="min-w-0 space-y-6">`). This caps the item to its grid track width, so
the table's `overflow-x-auto` wrapper now does its job.

## Verification
- `before-390-page-overflow.png`: full-page screenshot at 390px showing the page
  rendering at ~702px wide.
- `after-390-contained-scroll.png`: same repro after the fix — full-page screenshot
  stays 390px wide; the results table itself clips and scrolls within its own container
  (visible as truncated "ACTU..." column at the viewport edge), which is the WCAG 2.2
  reflow exception for two-dimensional tabular data.
- `document.documentElement.{clientWidth,scrollWidth}` measured equal (390/390) after
  the fix at both 390px and 768px viewports.
- `npm run typecheck` clean.
