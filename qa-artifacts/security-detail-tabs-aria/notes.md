# Security Detail tabs: complete ARIA tabs pattern — 8 settembre 2026

Addresses the Media-priority finding from
`qa-artifacts/responsive-accessibility-2026-09-08/report.md`: "Tab ARIA incompleti —
`frontend/src/pages/SecurityDetailPage.tsx:122` — Associazioni tab/panel e gestione
frecce/focus coerente."

## Before
`role="tablist"` / `role="tab"` / `role="tabpanel"` were present, but:
- no `id`/`aria-controls` linking each tab to its panel
- no `aria-labelledby` on the panel
- every tab button was in the natural tab order (no roving tabindex)
- no arrow-key navigation (Left/Right/Home/End) between tabs, per the WAI-ARIA
  [Tabs pattern](https://www.w3.org/WAI/ARIA/apg/patterns/tabs/)

## Fix
`frontend/src/pages/SecurityDetailPage.tsx`:
- each tab button: `id="tab-<key>"`, `aria-controls="tabpanel-<key>"`,
  `tabIndex={tab === key ? 0 : -1}` (roving tabindex — only the active tab is
  Tab-reachable, matching the pattern's keyboard model)
- the panel: `id="tabpanel-<tab>"`, `aria-labelledby="tab-<tab>"`, `tabIndex={0}`
  (focusable so focus has somewhere to land after activating a tab whose panel
  doesn't start with its own focusable element)
- `onKeyDown` on the tablist: `ArrowRight`/`ArrowLeft` move (and, matching this
  page's existing click-to-activate behavior, activate) the next/previous tab with
  wrap-around; `Home`/`End` jump to the first/last tab
- `focus-visible:outline` added to the tab buttons, matching the rest of the app's
  focus-ring convention

## Verification
- `document.querySelectorAll('[role="tab"]')`: only the active tab has `tabIndex 0`,
  each carries the correct `aria-controls`; the panel carries matching `id` and
  `aria-labelledby`.
- Keyboard: focus `Overview`, `ArrowRight` moves focus to and activates `Quotes`;
  `End` jumps to `Insider` (last tab); `ArrowRight` from there wraps back to
  `Overview`.
- No overflow regression at 390px (`clientWidth === scrollWidth`).
- `npm run typecheck` clean.
