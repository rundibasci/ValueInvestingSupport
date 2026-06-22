# Plan — H3: Screener UI

This plan outlines the steps required to implement the frontend Screener UI using React 18, TypeScript, TailwindCSS, and TanStack Query.

## Task Group 1: Types & API Services

1.1 Define TypeScript interfaces in a new file `frontend/src/api/screener.ts` to match the backend screener DTO structure:
*   `ScreenerRequest`: filter fields (`sector`, `exchange`, `mosMin`, `mosMax`, `scoreMin`, `scoreMax`, `roicMin`, `debtToEquityMax`, `dividendYieldMin`), `sortBy`, `sortDirection`, `page`, `pageSize`.
*   `ScreenerResultItem`: fields (`symbol`, `companyName`, `sector`, `exchange`, `currentPrice`, `compositeFairValue`, `marginOfSafety`, `totalScore`, `recommendation`).
*   `ScreenerResponse`: `results`, `page`, `pageSize`, `totalElements`, `totalPages`.

1.2 Implement API fetch functions in `frontend/src/api/screener.ts` using the central `apiFetch` client:
*   `fetchScreener(request: ScreenerRequest): Promise<ScreenerResponse>`
*   `fetchScreenerPresets(): Promise<Record<string, ScreenerRequest>>`
*   `fetchSectors(): Promise<string[]>`
*   `fetchExchanges(): Promise<string[]>`

## Task Group 2: Screener Filter Panel & Presets Component

2.1 Create `frontend/src/components/ScreenerPresetsBar.tsx`:
*   Fetch presets via TanStack Query and render buttons for "Graham", "Dividend", and "Quality".
*   On click, callback triggers applying all corresponding filter settings.

2.2 Create `frontend/src/components/ScreenerFilterPanel.tsx`:
*   Render Sector and Exchange dropdowns populated from backend endpoints.
*   Render HTML5 range inputs styled with Tailwind for:
    *   Margin of Safety (min/max range)
    *   Value Score (min/max range)
    *   ROIC (min value)
    *   Dividend Yield (min value)
    *   Debt-to-Equity (max value)
*   Include a "Reset Filters" button.
*   Ensure value labels update instantly as the user drags a slider.

## Task Group 3: Screener Table & Pagination Component

3.1 Create `frontend/src/components/ScreenerTable.tsx`:
*   Render headers for Ticker, Name, Sector, Exchange, Price, Fair Value, Margin of Safety, Total Score, Recommendation.
*   Attach click handlers to headers to toggle sort direction or change sorting column. Include sorting indicators (up/down arrows).
*   Format numbers appropriately (e.g., currency, percentages, ratios) and handle null values gracefully.
*   Implement Recommendation badges using Tailwind styles representing STRONG_BUY (emerald), QUALITY_VALUE (green), FAIR_VALUE (amber), and OVERVALUED (red).
*   Add row clicks navigating to `/securities/:symbol` via React Router's `useNavigate`.

3.2 Add Pagination Controls to the bottom of the table:
*   "Previous Page" and "Next Page" buttons, disabled when at boundaries.
*   Current page info (e.g., "Page 1 of 5").
*   Page size select dropdown supporting 20, 50, and 100 rows.

## Task Group 4: Page Integration & URL State

4.1 Replace the placeholder in `frontend/src/pages/ScreenerPage.tsx` (formerly rendering a placeholder in `App.tsx`):
*   Import `useSearchParams` from `react-router-dom` to serialize and synchronize filter/sort/pagination states in the URL.
*   Use TanStack Query's `useQuery` to fetch screener results, depending on the current search params as query keys.
*   Implement debouncing (e.g., 300ms) for filter changes before applying them to the URL search params.
*   Render `ScreenerPresetsBar`, `ScreenerFilterPanel`, and `ScreenerTable`.
*   Ensure a loading skeleton displays during fetch operations and an error state displays if the API request fails.
*   Include the mandatory MiFID II decision-support disclaimer in the page footer.

4.2 Update `frontend/src/App.tsx` to map `/screener` directly to `ScreenerPage`.

## Task Group 5: Verification & Tests

5.1 Add unit and integration tests under `frontend/src/pages/__tests__/ScreenerPage.test.tsx` using Vitest / React Testing Library:
*   Verify preset selection updates the UI inputs and triggers query parameters change.
*   Verify pagination clicks increment/decrement page parameter.
*   Verify sorting click updates sort parameters.
*   Verify range sliders react to input adjustments and apply correct debounced query filters.
*   Verify click on a table row triggers route navigation.
