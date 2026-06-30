# MA2 Moat & Quality Frontend Validation

## Acceptance Checks

- Review page shows a moat assessment card with classification, ROIC versus WACC chart, ROIC consistency, trend, spread, reinvestment rate, and explanatory text.
- Review page shows a capital allocation card with normalized shares outstanding trend, buyback/dilution percentage, shareholder yield, insider ownership state, and allocator classification.
- Review page valuation section shows P/E and EV/EBITDA historical band charts with percentile band, median, current value, and valuation position.
- Review page shows individual Graham stability criteria and a pass count.
- Screener results show moat strength and shares outstanding trend when data is present.
- Existing review, screener, portfolio, watchlist, and authentication routes still build.
- Missing MA1 data renders as an explicit unavailable/insufficient state rather than blank content.
- UI copy remains factual and decision-support oriented.

## Test Strategy

- TypeScript build catches contract drift and component prop errors.
- Existing frontend build validates routing and bundling.
- Add focused helper tests only if the repository already has frontend test infrastructure; otherwise validate helper behavior through typed display functions and build.

## Commands

- `cd frontend; npm run build`
- `git diff --check`

## Manual QA

- Inspect the review page structure with representative MA1 data if a local backend/demo fixture is available.
- Confirm charts fit at desktop and narrow widths without table/card overflow.
- Confirm missing-data labels are visible for empty series and partial histories.

## Merge Readiness

- Generated spec files are present and non-empty.
- Validation commands pass.
- Obsidian activity note is written.
- Changelog is updated by the merge workflow.
- Phase branch is pushed and merged to `main`.

## Known Risks

- If the frontend has no implemented comparison surface, comparison additions will remain a documented deferral rather than a new feature.
- Backend historical coverage may be sparse for some symbols; frontend must handle partial arrays without implying a complete 10-year history.
- Existing chart containers may need responsive constraints to prevent overlap on narrow viewports.
