# SC1 Universe Selection Criteria & Filtering API Plan

1. API contract and DTOs
   - Add criteria request, preview response, preview row, template, and seed response DTOs.
   - Support exchanges, countries, sectors, include/exclude sector mode, market-cap range, volume minimum, max-symbol cap, and sort order.
   - Normalize ticker and filter input casing predictably.

2. Universe selection service
   - Load symbols through `MarketDataClient.listSymbols(exchange)` for requested exchanges.
   - Apply country, sector, market-cap, volume, capping, and sort filters.
   - Return total matches, capped flag, warning, and preview rows.
   - Resolve built-in templates into criteria.

3. Admin controller
   - Add `GET /api/v1/admin/universe/templates`.
   - Add `POST /api/v1/admin/universe/preview`.
   - Add `POST /api/v1/admin/universe/seed`.
   - Preserve the existing `/api/v1/universe/seed?tickers=` endpoint unchanged.

4. Tests
   - Unit-test filtering, sorting, capping, and template resolution.
   - Controller-test the admin endpoints and request/response shape.
   - Verify compatibility of the existing explicit ticker seed endpoint.

5. Documentation and validation
   - Run focused backend tests for universe selection.
   - Run the backend Maven test suite if focused validation passes.
   - Update validation notes with commands and results.
