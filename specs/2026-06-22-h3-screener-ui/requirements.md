# H3 — Screener UI

## Context

H3 is the first interface in the Value Investing cycle: screening. It turns the existing authenticated frontend scaffold and backend screener API into a practical research surface for finding fundamentally sound companies that may trade below intrinsic value. It must make the user’s criteria and the resulting data legible, without presenting a screen as investment advice.

The backend already exposes authenticated endpoints for screening (`POST /api/v1/screener`), presets (`GET /api/v1/screener/presets`), sector options (`GET /api/v1/screener/sectors`), and exchange options (`GET /api/v1/screener/exchanges`). The screen request supports sector, exchange, margin-of-safety range, minimum value score, minimum ROIC, maximum debt-to-equity, minimum dividend yield, minimum revenue growth, sorting, and pagination. Each result includes price, composite fair value, margin of safety, total score, component scores, recommendation, and score date.

## Scope

- Replace the H1 screener placeholder at `/screener` with an authenticated React screen built with TypeScript, TailwindCSS, React Router v6, and TanStack Query.
- Provide an accessible filter panel for every currently supported screener criterion: sector, exchange, minimum and maximum margin of safety, minimum value score, minimum ROIC, maximum debt-to-equity, minimum dividend yield, and minimum revenue growth.
- Populate sector and exchange selectors from the API, with loading, empty, and recoverable error states.
- Provide Graham, Dividend, and Quality preset controls backed by the server-defined preset values. Selecting one replaces the current criteria, resets to the first page, and immediately runs the screen; users may then adjust any populated field.
- Show a results table with company, symbol, sector, exchange, current price, composite fair value, margin of safety, total value score, recommendation, and data-as-of date. Format numeric values and distinguish unavailable values without inventing a value.
- Support sorting by the backend-supported sort fields, direction toggling, server-side pagination, result count, first/previous/next/last navigation, and a page-size selector within supported server limits.
- Make every result row keyboard-accessible and navigate it to `/securities/:symbol`.
- Provide initial, loading/refetching, no-results, API-error, and invalid-filter states. Preserve the last successful results while a replacement query is loading where practical.
- Display the product’s decision-support/MiFID II boundary near results and avoid recommendation wording that implies personalised advice.

## Decisions

| Topic | Decision |
| --- | --- |
| Filter scope | Expose every field the current `ScreenerRequest` supports; do not fabricate filters that lack an API contract. |
| Filter interaction | Users explicitly apply/reset filters. Apply normalises empty values to omitted fields and returns to page 1. |
| Presets | Fetch values from the API; clicking Graham, Dividend, or Quality populates the editable form and runs it immediately. |
| Data ownership | The backend remains the authority for preset definitions, results, sorting, and pagination. The client only renders and serialises typed request state. |
| Server state | Use TanStack Query with a stable query key based on the applied request. Lookup lists and presets are independently cached. |
| Default query | Load the first page using the backend’s conservative/default ordering (total score descending) once authentication is ready. |
| Results emphasis | Prioritise transparent decision inputs: market price, calculated fair value, MoS, score, recommendation, and date. Scores supplement—not replace—the underlying data. |
| Navigation | Row activation sends the user to the existing security-detail route. Buttons and links inside rows must retain their normal semantics. |
| Accessibility & responsive design | Use labelled form controls, visible focus, keyboard-operable sorting/paging/rows, `aria-sort` table headers, announced async status/errors, and a usable small-screen representation without hiding essential content. |

## Out of Scope

- New screener criteria, backend query changes, saved screens, shareable URLs, exports, bulk actions, or alert creation.
- Editing or duplicating the backend preset definitions in the frontend.
- Security-detail interface work beyond navigating to it (H4).
- Portfolio, watchlist, dashboard, and alert features.
- Investment recommendations, trade execution, or advice.

## Constraints from Mission and Stack

- H3 must support the mission’s Screening → Fundamental Analysis progression, with transparent data before opinion and clear decision-support—not-advice—language.
- Retain the H1/H2 React 18, TypeScript strict-mode, TailwindCSS, React Router v6, authenticated API client, and TanStack Query foundation. Do not introduce a competing state or styling framework.
- Use the existing JWT-authenticated API client and respect its session-expiry behavior; frontend route protection never replaces backend authorization.
- Do not put secrets, credentials, token values, or financial data fixtures with sensitive information in source control or logs.
