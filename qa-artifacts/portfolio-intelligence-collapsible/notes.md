# Portfolio intelligence made collapsible — 8 settembre 2026

Requested live during the guided mobile walkthrough: the Portfolio page requires a lot
of scrolling once a portfolio is selected, mainly because of the "Portfolio intelligence"
block (weighted metrics, sector allocation chart, holding concentration, moat profile,
quality distribution, benchmark comparison).

## Change
`frontend/src/pages/PortfolioPage.tsx` (`AnalyticsDashboard`): the section's root
`<section>` became a native `<details open>` (matching the `<details>/<summary>`
convention already used elsewhere — Screener's "About the conservative preset",
Checklist's "More indicators"). The `<summary>` reproduces the full header (title,
description, snapshot timestamp) so the section stays informative even collapsed —
nothing is hidden by default, and closing it doesn't lose context.

- Native disclosure markers hidden (`list-none` + `[&::-webkit-details-marker]:hidden`
  for cross-browser consistency) and replaced with an explicit chevron `<svg>` that
  rotates via `group-open:rotate-90`, since the header's two-column flex layout
  (title/description left, snapshot right) isn't compatible with the browsers' native
  `::marker` rendering once `display` is overridden.
- `min-h-11` + `focus-visible:outline` on `<summary>`, matching the touch-target and
  focus-visible conventions used across the rest of the app.
- Defaults to `open` — behavior is unchanged until the user collapses it; nothing that
  was visible before is hidden by default.

## Verification
- `collapsed-390.png`: collapsed state at 390px — title, description, and snapshot date
  remain visible; "Allocation constraints" is immediately below with no diagnostics
  scroll in between.
- `open-1440.png`: desktop, expanded, chevron pointing down, header two-column layout
  intact, focus-visible ring shows on keyboard focus.
- Keyboard: `Tab` to the summary, `Enter` toggles open/closed (native `<details>`
  behavior, no custom JS needed).
- `document.documentElement.clientWidth === scrollWidth` still holds at 390px and
  1440px (no overflow regression from the `qa-artifacts/portfolio-overflow-fix/`
  commit right before this one).
- `npm run typecheck` clean.
