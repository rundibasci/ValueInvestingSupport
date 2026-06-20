# Requirements — Group F2: Portfolio CRUD & Holdings (M6)

## Scope

Implement the per-user Portfolio CRUD API and per-portfolio Holdings management API. This is the second delivery in **M6 (Portfolio)** on the roadmap.

| Phase | Deliverable |
|---|---|
| **F2** | `GET /api/v1/portfolios` (list), `POST /api/v1/portfolios` (create), `GET /api/v1/portfolios/{id}` (detail with holdings, weights, MoS), `POST /api/v1/portfolios/{id}/holdings` (add holding), `PUT /api/v1/portfolios/{id}/holdings/{holdingId}` (update holding), `DELETE /api/v1/portfolios/{id}/holdings/{holdingId}` (remove holding) |

---

## Context

### What exists already

- `Portfolio` JPA entity + `portfolio` table (V2): `id (UUID)`, `user_id (FK → app_user)`, `name (VARCHAR 255)`, `description (TEXT)`, `created_at`, `updated_at`, `holdings (OneToMany → Holding)`
- `Holding` JPA entity + `holding` table (V2): `id (UUID)`, `portfolio_id (FK → portfolio)`, `symbol (VARCHAR 20)`, `quantity (DECIMAL 15,6)`, `average_cost_basis (DECIMAL 15,4)`, `currency (VARCHAR 10)`, `added_at (TIMESTAMP)`
- `PortfolioRepository`: `findByUser(User)`, `findByUserOrderByCreatedAtDesc(User)` — already present
- `HoldingRepository`: `findByPortfolio(Portfolio)`, `findByPortfolioAndSymbol(Portfolio, String)`, `findAllDistinctSymbols()` — already present
- `SecurityRepository`: `findBySymbol(String)` — used to resolve securities for price/valuation lookup
- `PriceQuoteRepository`: `findTopBySecurityOrderByQuoteDateDesc(Security)` — used to fetch current price
- `ValuationResultRepository`: `findTopBySecurityOrderByValuationDateDesc(Security)` — used to fetch MoS and composite fair value
- Spring Security auth filter — `authentication.getName()` returns authenticated user's email; `UserRepository.findByEmail(String)` resolves the `User` entity
- Flyway migrations V1–V7 applied; next version is V8

### What this phase introduces

- Flyway migration `V8__portfolio_holding_index.sql` — adds `idx_holding_portfolio` and `idx_holding_portfolio_symbol` indexes (missing from V2)
- `PortfolioRepository.findByIdAndUser(UUID, User)` — ownership-safe portfolio lookup
- `HoldingRepository.findByPortfolioOrderByAddedAtDesc(Portfolio)`, `HoldingRepository.findByIdAndPortfolio(UUID, Portfolio)` — ordered listing and ownership-safe holding lookup
- DTOs: `CreatePortfolioRequest`, `PortfolioSummaryResponse`, `AddHoldingRequest`, `UpdateHoldingRequest`, `HoldingDetailItem`, `PortfolioDetailResponse`
- `PortfolioService` — all business logic in package `it.mazzoni.vis.portfolio`
- `PortfolioController` — 6 endpoints in package `it.mazzoni.vis.portfolio`
- Testcontainers PostgreSQL integration test `PortfolioIT`

---

## Decisions

### Multiple portfolios per user — named, not auto-created

Unlike the F1 Watchlist (single auto-created container), each `POST /api/v1/portfolios` creates a distinct, named portfolio. Users may own as many portfolios as they wish. The list endpoint returns all of them ordered by `createdAt DESC`. The portfolio container is visible in all responses (unlike the hidden `Watchlist` container in F1).

### Multiple lots per symbol — no duplicate guard

`POST /api/v1/portfolios/{id}/holdings` allows the same symbol to appear more than once in a portfolio (representing separate purchase lots at different cost bases). No `409 Conflict` on duplicate symbols. `PUT` targets a specific `holding.id`, not a symbol, so updating one lot does not affect others.

### Portfolio detail — weights and MoS computed from DB, no live API calls

`GET /api/v1/portfolios/{id}` enriches each holding with:
- `currentPrice` — from the most recent `PriceQuote.close` for the symbol (populated by B3 nightly refresh); null if the security is not yet in the DB or has no price quote
- `currentValue = quantity × currentPrice`; null if `currentPrice` is null
- `totalValue` = sum of all non-null `currentValue` across holdings
- `weightPercent = (currentValue / totalValue) × 100`; null if `totalValue` is null or zero
- `compositeFairValue`, `marginOfSafety`, `recommendation` — from the most recent `ValuationResult` for the symbol; null if no valuation exists
- `weightedMoS` = sum of `(weightPercent / 100) × marginOfSafety` for holdings where both are present; null if none qualify

No `MarketDataClient` / FMP call is made at request time. All enrichment reads from the local DB.

### Ownership enforcement via 404 (not 403)

- Portfolio lookup: `portfolioRepository.findByIdAndUser(id, user)` → 404 if not found or belongs to another user.
- Holding lookup: after verifying portfolio ownership, `holdingRepository.findByIdAndPortfolio(holdingId, portfolio)` → 404 if holding does not belong to that portfolio.
- Neither 403 nor resource existence is disclosed to the caller.

### Symbol normalisation

Symbols are uppercased on write (`symbol.toUpperCase()`) and stored in uppercase. This matches the convention established in F1 and throughout the ingestion pipeline.

---

## Request / Response Shapes

### `GET /api/v1/portfolios`

```json
[
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Growth Portfolio",
    "description": "Long-term value holds",
    "holdingCount": 3,
    "createdAt": "2026-06-20T10:00:00",
    "updatedAt": "2026-06-20T11:30:00"
  },
  {
    "id": "7b8c9d1e-2f3a-4b5c-6d7e-8f9a0b1c2d3e",
    "name": "Dividend Portfolio",
    "description": null,
    "holdingCount": 0,
    "createdAt": "2026-06-20T09:00:00",
    "updatedAt": "2026-06-20T09:00:00"
  }
]
```

Empty array `[]` if no portfolios. Ordered newest-first (`createdAt DESC`).

---

### `POST /api/v1/portfolios`

Request body:
```json
{
  "name": "Growth Portfolio",
  "description": "Long-term value holds"
}
```
- `name` is required and must be non-blank; `description` is optional (nullable).

Response: `201 Created`
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "Growth Portfolio",
  "description": "Long-term value holds",
  "holdingCount": 0,
  "createdAt": "2026-06-20T10:00:00",
  "updatedAt": "2026-06-20T10:00:00"
}
```

Error cases:
- `400 Bad Request` — blank or missing `name`

---

### `GET /api/v1/portfolios/{id}`

Response: `200 OK`
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "Growth Portfolio",
  "description": "Long-term value holds",
  "totalValue": 3600.00,
  "weightedMoS": 14.5,
  "holdings": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "symbol": "AAPL",
      "quantity": 10,
      "averageCostBasis": 150.00,
      "currency": "USD",
      "currentPrice": 180.00,
      "currentValue": 1800.00,
      "weightPercent": 50.00,
      "compositeFairValue": 210.00,
      "marginOfSafety": 16.67,
      "recommendation": "QUALITY_VALUE",
      "addedAt": "2026-06-20T10:15:00"
    },
    {
      "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
      "symbol": "MSFT",
      "quantity": 5,
      "averageCostBasis": 320.00,
      "currency": "USD",
      "currentPrice": 360.00,
      "currentValue": 1800.00,
      "weightPercent": 50.00,
      "compositeFairValue": null,
      "marginOfSafety": null,
      "recommendation": null,
      "addedAt": "2026-06-20T10:20:00"
    }
  ],
  "createdAt": "2026-06-20T10:00:00",
  "updatedAt": "2026-06-20T10:20:00"
}
```

Holdings ordered newest-first (`addedAt DESC`). Fields `currentPrice`, `currentValue`, `weightPercent`, `compositeFairValue`, `marginOfSafety`, `recommendation` are all nullable. `totalValue` and `weightedMoS` are null if no holdings have a price quote.

Error cases:
- `404 Not Found` — portfolio not found or does not belong to authenticated user

---

### `POST /api/v1/portfolios/{id}/holdings`

Request body:
```json
{
  "symbol": "AAPL",
  "quantity": 10,
  "averageCostBasis": 150.00,
  "currency": "USD"
}
```
- `symbol` required, non-blank (uppercased on store); `quantity` required, positive; `averageCostBasis` and `currency` optional.

Response: `201 Created`
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "symbol": "AAPL",
  "quantity": 10,
  "averageCostBasis": 150.00,
  "currency": "USD",
  "currentPrice": 180.00,
  "currentValue": 1800.00,
  "weightPercent": null,
  "compositeFairValue": 210.00,
  "marginOfSafety": 16.67,
  "recommendation": "QUALITY_VALUE",
  "addedAt": "2026-06-20T10:15:00"
}
```

Note: `weightPercent` is null in the add-holding response because it cannot be computed without the full portfolio context. The correct weight is available via `GET /api/v1/portfolios/{id}`.

Error cases:
- `400 Bad Request` — blank `symbol` or null/non-positive `quantity`
- `404 Not Found` — portfolio not found or does not belong to authenticated user

---

### `PUT /api/v1/portfolios/{id}/holdings/{holdingId}`

Request body:
```json
{
  "quantity": 15,
  "averageCostBasis": 145.00,
  "currency": "USD"
}
```
- `quantity` required, positive; `averageCostBasis` and `currency` nullable (null clears the existing value).

Response: `200 OK` with updated `HoldingDetailItem`.

Error cases:
- `400 Bad Request` — null or non-positive `quantity`
- `404 Not Found` — portfolio or holding not found / belongs to another user or portfolio

---

### `DELETE /api/v1/portfolios/{id}/holdings/{holdingId}`

Response: `204 No Content`

Error cases:
- `404 Not Found` — portfolio or holding not found / belongs to another user or portfolio

---

## Authorization

All 6 endpoints require authentication. Any role (ADMIN, ADVISOR, INVESTOR) is accepted.

| Endpoint | Required role |
|---|---|
| All `GET/POST/PUT/DELETE /api/v1/portfolios/**` | `hasAnyRole("ADMIN","ADVISOR","INVESTOR")` |

---

## Out of Scope

- Portfolio simulation (`POST /api/v1/portfolios/{id}/simulate`) — belongs to F3
- Rebalancing (`GET /api/v1/portfolios/{id}/rebalance`) — belongs to F4
- `DELETE /api/v1/portfolios/{id}` — portfolio-level deletion not in roadmap for F2; holdings can be removed individually
- Live FMP / Yahoo Finance calls at request time — all enrichment reads from local DB
- Redis caching of portfolio responses — future hardening pass
- Portfolio sharing or multi-user access — each portfolio is owned exclusively by its creator
- P&L computation (`currentValue - averageCostBasis × quantity`) — out of scope for MVP v1 (roadmap)
- Frontend changes — PFD1 (Group PFD) will wire up the full HTML demo
