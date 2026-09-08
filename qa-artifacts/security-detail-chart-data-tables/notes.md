# Security Detail charts: accessible data table equivalent — 8 settembre 2026

Closes the last Media-priority finding from
`qa-artifacts/responsive-accessibility-2026-09-08/report.md`: "Grafici stretti e dati
storici senza equivalente tabellare nel componente —
`frontend/src/pages/SecurityDetailPage.tsx:80`, `:88` — Riepilogo trend e tabella dati
accessibile."

## Fix
`frontend/src/pages/SecurityDetailPage.tsx`:
- **`Chart`** (the shared component behind the Financials, Ratios, Financial health, and
  Dividends tabs — one fix, four tabs covered) now renders a `ChartDataTable` below the
  `ResponsiveContainer`: a `<details>`/`<summary>` ("View chart data as a table",
  collapsed by default so it doesn't add scroll for people who don't need it) revealing
  a table with one row per period and one column per line/series, using the same
  `valueFormatter` as the chart's own axis/tooltip.
- **`PriceHistoryChart`** (Quotes tab) gets its own `PriceHistoryTable` for the same
  reason, with per-column formatters (`date`, `money` for Close, `number` for Volume,
  the Volume column only rendered when the series has it) since price/volume don't
  share one formatter the way `Chart`'s generic lines do. Rows sorted newest-first,
  in a `max-h-96 overflow-auto` container with a sticky header so long ranges (10y/max)
  don't push the rest of the page down.
- Both toggles use the existing `<details>/<summary>` convention (Screener's "About the
  conservative preset", Checklist's "More indicators") with `min-h-11` and
  `focus-visible:outline` to match.

## Verification
- `financials-chart-table-390.png` / `-1440.png`: the shared `Chart` table on the
  Financials tab (Annual performance: Revenue/Net income/Free cash flow by year),
  correct at both widths.
- `price-history-table-390.png` / `-rows-390.png`: the Quotes tab's price/volume table,
  expanded, showing Date/Close/Volume rows.
- No page-overflow regression at 390px or 1440px (`clientWidth === scrollWidth`).
- `npm run typecheck` clean.
