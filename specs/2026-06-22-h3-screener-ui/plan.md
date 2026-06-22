# H3 — Screener UI Plan

## 1. Confirm and model the screener contract

1.1 Inspect the existing screener endpoints and DTOs; document allowed request fields, sort fields/directions, pagination limits, nullable values, and result meanings.

1.2 Add strict frontend types and request/response mapping for screener queries, result items, presets, sectors, and exchanges. Omit empty filter values rather than serialising misleading defaults.

1.3 Centralise calls through the authenticated API client and ensure 401/session-expiry behavior stays consistent with H2.

## 2. Establish query and filter state

2.1 Build a filter form with all supported sector, exchange, MoS, score, ROIC, debt-to-equity, dividend-yield, and revenue-growth criteria.

2.2 Fetch and render sector/exchange choices and presets with independent loading, retry, and unavailable-data states.

2.3 Separate editable form state from applied query state. Implement Apply, Reset, validation for incompatible ranges/invalid numeric entries, page reset on criteria changes, and a stable TanStack Query key.

2.4 Implement server-provided Graham, Dividend, and Quality actions that replace form state, reset paging, and execute a query.

## 3. Build the screener research surface

3.1 Replace the `/screener` placeholder with a responsive desktop/mobile layout that presents criteria, active context, result count, and decision-support disclaimer clearly.

3.2 Build a semantic results table for symbol/company, sector/exchange, price, fair value, MoS, value score, recommendation, and score date. Format currency, percentages, dates, and missing values consistently.

3.3 Add sortable columns using server-side sort field/direction values, visible sort indicators, and `aria-sort` semantics.

3.4 Add server-side pagination: total summary, page-size selection, and first/previous/next/last controls with disabled states and retained context.

3.5 Make each result row reachable by keyboard and mouse, then navigate it to `/securities/:symbol` without preventing normal interactive child controls.

## 4. Handle data and interaction edge cases

4.1 Provide coherent first-load, refetch, empty-result, malformed/invalid-filter, lookup failure, and query failure states. Keep a last successful table visible while refetching when safe.

4.2 Ensure mobile users can inspect all essential fields via a horizontally scrollable semantic table or an equivalent accessible condensed view.

4.3 Review content for transparent, non-advisory language and display a concise MiFID II/decision-support boundary beside the results.

## 5. Verify and prepare merge evidence

5.1 Add frontend tests for request serialisation, all supported filters, apply/reset, presets, sorting, pagination, status/error states, result navigation, and authentication-expiry integration.

5.2 Run TypeScript checks, frontend tests, production build, and relevant existing backend screener tests.

5.3 Perform browser checks for keyboard-only use, visible focus, screen-reader announcements, responsive layouts, sorting/paging correctness, and row navigation.
