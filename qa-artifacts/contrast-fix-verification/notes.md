# Contrast fix verification — 8 settembre 2026

Follow-up to `qa-artifacts/responsive-accessibility-2026-09-08/report.md`, Alta item
"Testi slate-500 su fondi scuri sotto contrasto 4,5:1".

## Fix
Replaced `text-slate-500` with `text-slate-400` across all 17 frontend files that used it
as muted/label text on this app's dark (slate-950/900/800) surfaces (89 occurrences).
The app has no light-background surfaces outside a small white icon chip, so this is a
uniform design-token correction rather than a per-page patch.

## Contrast math
- slate-500 (#64748b) on slate-950 (#020617): ~4.24:1 — below AA 4.5:1 for normal text.
- slate-500 (#64748b) on slate-900 (#0f172a): ~3.75:1 — below AA 4.5:1.
- slate-400 (#94a3b8) on slate-950 (#020617): ~7.9:1 — passes AA and AAA.
- slate-400 (#94a3b8) on slate-900 (#0f172a): ~7.0:1 — passes AA and AAA.

## Empirical check
`npm run dev -- --port 5176` (separate port; the audit's own docker container is still
bound to 5173), then an in-page script walked every leaf text node on the login route,
computed actual foreground/effective-background contrast via getComputedStyle, and
flagged anything under 4.5:1. Result: zero violations (`login-contrast-fixed-390.png`).
Other routes require an authenticated session/mocked API to reach and were not
re-screenshotted here, but use the same slate-400-on-dark-surface token now applied
uniformly, so the same math applies.

## Scope not covered
This pass only fixes the `text-slate-500` token swap. It does not re-verify contrast
under transparent/semi-transparent backgrounds (e.g. `bg-slate-950/55`) pixel-by-pixel,
which the original audit flagged as a limitation of its own measurement too.
