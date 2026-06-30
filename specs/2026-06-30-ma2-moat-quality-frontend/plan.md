# MA2 Moat & Quality Frontend Plan

1. Inspect frontend contracts and existing analytical surfaces.
   - Locate review-page response types, valuation chart components, screener result columns, and any comparison UI.
   - Confirm MA1 fields are already exposed by the review and screener APIs.
   - Identify reusable formatting and availability patterns for unavailable history.

2. Add typed moat and quality models.
   - Extend the frontend review API types with moat, capital allocation, valuation band, and stability sections.
   - Extend screener result types with moat strength and shares outstanding trend fields.
   - Keep names aligned with backend DTOs and add tolerant optional fields for partial history.

3. Build review-page business-quality sections.
   - Add a moat assessment card with moat badge, ROIC versus WACC chart, consistency, trend, spread, and reinvestment rate.
   - Add a capital allocation card with normalized shares outstanding chart, buyback/dilution summary, shareholder yield, insider ownership availability, and allocator badge.
   - Add historical valuation band charts for P/E and EV/EBITDA near existing valuation outputs.
   - Add a compact stability scorecard with individual Graham stability criteria.

4. Extend screener and comparison surfaces.
   - Add moat strength and shares outstanding trend columns to screener results.
   - Add moat strength, capital allocator type, and valuation position fields to the comparison surface if an implemented comparison view exists.
   - Preserve responsive table behavior and missing-data display.

5. Validate and document.
   - Run frontend build/typecheck.
   - Run backend tests only if implementation requires backend contract changes.
   - Run `git diff --check`.
   - Update the Obsidian activity note with implemented behavior, validation results, changed areas, and merge status.
