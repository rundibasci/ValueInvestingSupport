# H3 — Screener UI Validation

H3 is complete and mergeable only when all checks below pass.

## Automated checks

- Frontend TypeScript strict-mode check, tests, and production build succeed.
- Typed API-client tests verify all supported filter fields are correctly serialised, blank values are omitted, page changes preserve applied criteria, and sort changes reset to the first page.
- Component/integration tests cover loading lookup data, applying and resetting filters, invalid ranges, server-defined presets, result rendering/formatting, empty results, query errors and retry, sorting, pagination, page-size changes, and navigation to `/securities/:symbol`.
- Accessibility-focused tests verify labels, keyboard activation, visible focus hooks, `aria-sort`, disabled pagination semantics, and announced loading/error/result status where the test setup supports them.
- Existing backend screener unit/integration tests remain green; no frontend change weakens its authenticated endpoint boundary.

## Browser acceptance checks

| Scenario | Expected result |
| --- | --- |
| Open `/screener` while authenticated | The initial server-sorted page loads with a clear count and decision-support disclaimer. |
| Open `/screener` while signed out | H2 route protection redirects to login, then returns to the screener after successful authentication. |
| Select sector/exchange and numeric criteria, then Apply | The request contains the chosen values, results and count update, and pagination returns to page 1. |
| Enter minimum MoS greater than maximum MoS | The form gives a clear, accessible validation error and does not send an invalid query. |
| Click Graham, Dividend, or Quality | The server-provided criteria populate the editable controls and results refresh automatically from page 1. |
| Change a sortable column | Sort direction is visible and announced; results reflect the server response and page is reset appropriately. |
| Move through result pages or change page size | Counts, disabled controls, rows, and retained criteria remain accurate; no client-side reordering disguises server data. |
| Query returns no matches | The page explains that no companies match, retains criteria, and offers a clear reset path. |
| Lookup or screen request fails | A non-sensitive error and retry are available; previously loaded results remain usable during a safe refetch. |
| Activate a result row with Enter/Space or pointer | Navigation reaches `/securities/:symbol` for that result. |
| Test narrow viewport and keyboard-only navigation | Essential data remains inspectable, controls remain operable, focus is obvious, and no keyboard trap occurs. |

## Merge gates

- The screen exposes every current backend-supported filter and no unsupported or fake criteria.
- Presets originate from `GET /api/v1/screener/presets`; frontend values do not drift from backend definitions.
- All sorting and pagination are server-driven, correctly represented in the request, and accessible in the UI.
- The UI clearly distinguishes price, calculated fair value, margin of safety, score, recommendation, and score date; unavailable data is not rendered as zero or an invented value.
- Language preserves the mission’s data-first, transparent decision-support boundary and includes an appropriate MiFID II disclaimer.
- No secrets, JWTs, refresh cookies, or sensitive values are added to source, logs, test output, browser storage, or URLs.
- The working tree contains only intentional H3 specification changes plus the pre-existing user-owned log files; active branch is `feature/h3-screener-ui`.
