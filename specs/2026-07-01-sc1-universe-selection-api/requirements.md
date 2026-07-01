# SC1 Universe Selection Criteria & Filtering API Requirements

## Scope

- Add backend APIs for criteria-based universe preview and seeding.
- Keep the feature admin-scoped and non-demo, matching the existing universe seed surface.
- Reuse the existing `MarketDataClient.listSymbols(exchange)` capability instead of introducing a new provider client.
- Return preview results before full seeding so admins can estimate universe size and composition.
- Add built-in universe templates for common value-investing workflows.

## Exclusions

- Group K/K1/K2/K3 cloud deployment work is explicitly excluded.
- React universe curation UI belongs to SC2 and is not part of this phase.
- Persistent saved custom templates are deferred; this phase exposes configured/built-in templates only.
- Excluding already-seeded symbols from screener results belongs to SC2.

## Decisions

- The next phase was selected as SC1 because PW2 is merged and the roadmap lists M18/Group SC before RD1, L, RD2, and K.
- Existing explicit ticker seeding at `/api/v1/universe/seed?tickers=` remains supported for compatibility.
- Criteria APIs live under `/api/v1/admin/universe` to match the roadmap wording and admin-only intent.
- FMP stock-list metadata is the preview source. If market cap or volume is unavailable from the provider entry, filtering treats the missing value as non-matching only when the corresponding criterion is supplied.

## Assumptions

- The FMP stock list entry contains enough metadata for symbol, company name, exchange, sector, market cap, price, and volume preview fields in this codebase.
- Country metadata may be absent from stock-list rows; country filters are applied only when a country value is available.
- The seed action may be synchronous and return the existing per-symbol `SeedResult` list because existing seed APIs are synchronous.
- Built-in templates can be implemented in Java configuration code for this phase; external YAML customization can be added later without changing the public API.

## Dependencies

- `MarketDataClient.listSymbols(exchange)`.
- Existing `SeedService.seedTickers(List<String>)`.
- Existing Spring Security admin authorization conventions.
- Existing backend test stack with MockMvc and unit tests.
