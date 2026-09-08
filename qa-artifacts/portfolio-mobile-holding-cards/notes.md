# Portfolio holdings: mobile card layout instead of wide tables — 8 settembre 2026

Addresses the Media-priority finding from
`qa-artifacts/responsive-accessibility-2026-09-08/report.md`: "Tabelle gestione
portfolio/import molto larghe, da 736 a 1216 px minimi... Riepilogo mobile e modifica di
una posizione alla volta." The original audit only tested empty portfolios (its own
documented limitation), so this is the first populated-portfolio pass on these tables.

## Populated-state overflow found first
Adding real holdings to a portfolio (via `POST /api/v1/portfolios/{id}/holdings`, since
the "Add holding" UI control was already disabled independent of this work) surfaced a
page-level horizontal overflow at 390px (`scrollWidth` 616–777 vs `clientWidth` 390) from
two tables that only render once `holdings.length > 0`, so the earlier empty-state
Portfolio audit and this session's earlier overflow fix never exercised them:
- the primary holdings table (`min-w-[46rem]`/736px, with per-row Review/Remove actions)
- the `ConservativeReviewPack` review table (`min-w-[64rem]`/1024px, rationale +
  validation columns)

## Fix
`frontend/src/pages/PortfolioPage.tsx`: both tables now render as a `<ul>` of per-holding
cards below `lg` (1024px) and the original `<table>` (unchanged, still in its
`overflow-x-auto` wrapper) at `lg:` and above — same `hidden lg:block` / `lg:hidden`
split already used by the Screener page's mobile card list.

- **Primary holdings card**: symbol + current value header, a 2x2 metric grid
  (quantity, weight, MoS, liquidity), then Review/Remove actions — "one position at a
  time," as the audit suggested, rather than a 6-column table.
- **Review-pack card**: symbol + price + portfolio weight header, MoS/valuation/score,
  then the rationale and validation findings as labelled paragraphs.

A third, wider table (`min-w-[62rem]`, the rebalance-proposal table with 10 columns) was
left as contained horizontal scroll: it already sits in a plain `overflow-x-auto`
wrapper with no risky ancestor grid, so it was not causing page overflow, and it's only
reachable after running a simulation - a full card redesign there was judged lower
value for this pass.

## Verification
- Reproduced the overflow, then added `KO`/`MSFT`/`JNJ` holdings via the portfolio API
  to a real portfolio to test the populated state (no UI control to add holdings yet).
- `holdings-cards-390.png` / `review-pack-cards-390.png`: both card lists at 390px,
  fully readable, Review/Remove actions present.
- `desktop-table-1440-unchanged.png`: the original tables still render unchanged at
  1440px (`lg:` and above).
- `document.documentElement.clientWidth === scrollWidth` at 390px and 1440px after the
  fix (was 390 vs 616–777 before).
- `npm run typecheck` clean.
