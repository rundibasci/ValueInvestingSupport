# Login form pushed below the fold on mobile — fixed 8 settembre 2026

Addresses the Media-priority finding from
`qa-artifacts/responsive-accessibility-2026-09-08/report.md`: "A 390 px, il campo email
inizia a y=1042 px (schermata alta 844 px)... Marketing e spaziatura precedono il
login." Suggested intervention: "Form prima dell'introduzione o introduzione compatta."

## Fix
`frontend/src/pages/LoginPage.tsx`: the page is a `grid lg:grid-cols-[1.05fr_.95fr]`
with two `<section>`s (marketing/branding, then the sign-in form) — below `lg` (1024px)
that grid collapses to a single stacked column in DOM order, pushing the form under all
the marketing copy. Reordered visually only, via Tailwind `order-*`, without touching
DOM order:
- marketing section: `order-2 lg:order-1`
- form section: `order-1 lg:order-2`

Safe because the marketing panel has no focusable/interactive elements (just the logo,
headline, and two paragraphs), so keyboard tab order is unaffected by the visual reorder.
The divider border (`border-b`/`lg:border-r`) moved from the marketing section to the
form section so it still separates the two panels regardless of which renders first.

## Verification
- `after-390-form-first.png`: the email field now sits at y≈541 on an 844px-tall 390px
  viewport (down from y=1042, previously below the fold) — visible without scrolling,
  even with the "session expired" banner shown above it.
- `after-1440-unchanged.png`: desktop layout (marketing left, form right) pixel-for-pixel
  the same arrangement as before, only reached via `order` instead of DOM position.
- `npm run typecheck` clean.
