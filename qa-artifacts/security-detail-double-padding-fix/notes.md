# Security Detail: nested <main> and doubled padding — fixed 8 settembre 2026

Addresses two related Media-priority findings from
`qa-artifacts/responsive-accessibility-2026-09-08/report.md` that share one root cause:
"Doppio padding riduce spazio utile" and (from "Aspetti positivi") "dettaglio contiene un
main annidato".

## Root cause
`securities/:symbol` is routed inside `<AppShell />`
(`frontend/src/App.tsx`), whose own `<main>` already provides
`mx-auto w-full max-w-7xl px-5 py-8 lg:px-8`. `SecurityDetailPage` rendered its own
second `<main>` (in all four of its early-return states, plus the primary return) with
the *same* `max-w-7xl px-5 py-8 lg:px-8` — an accessibility-tree main-landmark nested
inside another main-landmark, and horizontal padding applied twice, wasting ~40px of
usable width on each side on a 390px screen (~20%).

## Fix
`frontend/src/pages/SecurityDetailPage.tsx`: all five `<main>` returns changed to
`<div>`, and the padding/width classes already supplied by AppShell's `<main>`
(`mx-auto`, `max-w-7xl`, `px-5`, `py-8`, `lg:px-8`) removed; the narrower `max-w-3xl`
used by the two "unavailable" empty states is kept (it's intentionally narrower than the
shell's `max-w-7xl`, not a duplicate), as is each state's own vertical `py-*` rhythm.

## Verification
- `document.querySelectorAll('main').length === 1` on the route (was 2).
- Content now starts at `left: 20px` from the viewport edge at 390px (a single px-5),
  not 40px.
- `after-390.png` / `after-1440.png`: no visual regression at either width.
- `npm run typecheck` clean.
