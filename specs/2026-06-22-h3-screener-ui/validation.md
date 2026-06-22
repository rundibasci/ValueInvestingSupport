# Validation — H3: Screener UI

H3 is complete and ready to be merged only when all validation checks below pass.

## Automated Checks

*   **TypeScript check**: Running `npm run build` or `npx tsc --noEmit` in the frontend directory succeeds without errors.
*   **Production Build**: Building the frontend app via `npm run build` finishes successfully without bundler warnings or errors.
*   **Vitest Tests**: Frontend unit tests verify:
    *   Dynamic loading of sectors, exchanges, and presets.
    *   Selecting a preset (Graham, Dividend, Quality) updates the filter UI state and updates URL search parameters.
    *   Dragging range inputs updates numerical indicators and triggers the debounced search.
    *   Clicking table headers updates the sorting parameters (`sortBy`, `sortDirection`).
    *   Paginating via Prev/Next buttons or selecting a new page size triggers updates.
    *   Clicking a row triggers a route change to `/securities/:symbol`.

## Browser Acceptance Checks

| Scenario | Expected Result |
| :--- | :--- |
| **Visit `/screener`** | The page loads; default filters display; Sector and Exchange dropdowns populate with correct options; results display in the table with proper pagination. |
| **Click Presets** | Clicking "Graham", "Dividend", or "Quality" preset buttons updates the range sliders and dropdown selects instantly, fetching the matching filtered results. |
| **Adjust Numerical Sliders** | Adjusting a slider (e.g., setting MoS min to 15%) updates the inline label indicator; after a short debounce, the table shows updated results and the URL query changes. |
| **Click Reset Filters** | Clicking "Reset Filters" clears all sliders/dropdowns back to system default states, updating the URL and fetching unfiltered/default results. |
| **Sort Columns** | Clicking the "Total Score" header toggles sorting order between DESC and ASC, showing sorting indicators (arrows) and updating results dynamically. |
| **Paginate Results** | Clicking "Next" loads the next page of results; page size selector updates rows displayed; boundaries disable the respective buttons. |
| **Click Row** | Clicking any row in the table navigates the browser to `/securities/{symbol}` detail page. |
| **Disclaimer Footer** | The MiFID II disclaimer remains visible at the bottom of the page in all layout sizes. |
| **Responsive Viewports** | On mobile/tablet screens, the layout scales cleanly; filter section collapses into a drawer, accordion, or top section without breaking page scrolling. |

## Merge Gates

*   **URL Parameter Integrity**: Reloading the browser with active URL parameters (e.g., `/screener?sector=Technology&scoreMin=60`) loads the page with those exact filters preset and displays the corresponding search results.
*   **Memory-Only Tokens**: No authentication tokens (access token, refresh token) are written to localStorage, sessionStorage, or URL query parameters during screener searches.
*   **Design Alignment**: Layout uses the established slate background (`bg-slate-900`/`bg-slate-950`) and emerald values (`text-emerald-500`) with high contrast focus borders (`focus:ring-2 focus:ring-emerald-500`) for accessibility.
*   **Error Handling**: If the backend screener API fails (e.g. 500 error or token expired), the page shows a clean error boundary alert instead of crashing, with a retry trigger.
