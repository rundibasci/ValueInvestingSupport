# Requirements — H3: Screener UI

## Context

Following the completion of the React Authentication UI ([H2](file:///specs/2026-06-21-h2-authentication-ui)), the next phase of the roadmap is **H3: Screener UI**. The backend stock screener is already fully functional on the `/api/v1/screener` endpoint, supporting complex filters, sorting, presets, and pagination ([D1/D2](file:///specs/2026-06-20-d1-d2-screener-and-scoring)).

The goal of H3 is to implement the frontend user interface that connects to this backend API, allowing advisors and investors to discover undervalued, fundamentally strong securities.

## Scope

*   **Filter Panel**:
    *   Dropdowns populated dynamically from the backend for **Sector** (`GET /api/v1/screener/sectors`) and **Exchange** (`GET /api/v1/screener/exchanges`).
    *   Range sliders for numeric filters with visible, real-time value indicators:
        *   **Value Score** (min/max): 0 to 100
        *   **Margin of Safety** (min/max): -100% to 100%
        *   **ROIC** (min): 0% to 100%
        *   **Dividend Yield** (min): 0% to 50%
        *   **Debt-to-Equity** (max): 0.0 to 10.0
    *   Clear/Reset button to restore all filters to their defaults.
*   **Presets Bar**:
    *   Buttons to quickly load and apply the three standard backend presets (Graham, Dividend, and Quality) fetched via `GET /api/v1/screener/presets`.
*   **Screener Table**:
    *   Columns: Ticker (symbol), Name (companyName), Sector, Exchange, Price (currentPrice), Fair Value (compositeFairValue), Margin of Safety, Total Score, Recommendation.
    *   Headers supporting clickable sorting toggles (changes `sortBy` and `sortDirection`).
    *   Color-coded badges indicating recommendation types:
        *   `STRONG_BUY`: Deep emerald/green badge (e.g., text-emerald-800 bg-emerald-100)
        *   `QUALITY_VALUE`: Soft green badge (e.g., text-green-800 bg-green-100)
        *   `FAIR_VALUE`: Amber/yellow badge (e.g., text-amber-800 bg-amber-100)
        *   `OVERVALUED`: Red badge (e.g., text-red-800 bg-red-100)
    *   Row-click handler navigating the user to the respective security detail page (`/securities/:symbol`).
*   **Pagination & Page Size**:
    *   Controls to paginate search results (Prev, Next, Current page index).
    *   Dropdown to configure page size (options: 20, 50, 100).
*   **URL State Synchronization**:
    *   Synchronize screener filter, sorting, and pagination parameters with the browser's URL query parameters. This allows users to bookmark or share a specific filtered view of the screener.

## Decisions

| Topic | Decision |
| :--- | :--- |
| **API Integration** | Consume endpoints via TanStack Query. Use `/api/v1/screener` inside `useQuery` where the serialized filter state acts as the query key, triggering auto-refetch on filter/page/sort adjustments. |
| **URL State** | Use standard React Router search params for synchronizing filter state. When filters change, update the URL; the page state will drive its local state from the URL values to maintain a single source of truth. |
| **Range Sliders** | Standard HTML `<input type="range">` styled with Tailwind. This avoids introducing complex external slider dependencies, keeping the bundle light and accessible. |
| **Performance** | Debounce rapid range slider updates (e.g., 300ms delay) to prevent hammering the backend API with requests while dragging a slider. |

## Out of Scope

*   Custom screener preset creation/editing persisted to the backend. Only the 3 built-in system presets (Graham, Dividend, Quality) are supported.
*   Adding/removing securities from watchlists directly within the screener table (delegated to [H6](file:///specs/2026-06-21-f3-portfolio-builder)).
*   Exporting screener results to CSV/Excel (to be addressed in a future quality/reporting milestone).

## Constraints from Mission and Stack

*   **Design System**: Must match the established dark-slate foundation and emerald values from the platform [mission.md](file:///specs/mission.md) and [tech-stack.md](file:///specs/tech-stack.md).
*   **Accessibility (a11y)**: All interactive elements must support keyboard navigation, clear focus states, and aria-labels for screen readers.
*   **MiFID II Disclaimer**: A mandatory decision-support disclaimer must be clearly visible in the screener UI footer.
