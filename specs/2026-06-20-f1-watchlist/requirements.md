# Requirements — Group F1: Watchlist (M6 start)

## Scope

Implement the per-user Watchlist CRUD API and the active-alerts read endpoint. This is the first delivery in **M6 (Portfolio)** on the roadmap.

| Phase | Deliverable |
|---|---|
| **F1** | `GET /api/v1/watchlist`, `POST /api/v1/watchlist`, `PUT /api/v1/watchlist/{id}`, `DELETE /api/v1/watchlist/{id}` — item CRUD with alert thresholds; `GET /api/v1/watchlist/alerts` — list active alerts for current user |

---

## Context

### What exists already

- `Watchlist` JPA entity + `watchlist` table (V2): `id (UUID)`, `user_id (FK → app_user)`, `name (VARCHAR)` — user-owned container
- `WatchlistItem` JPA entity + `watchlist_item` table (V2): `id (UUID)`, `watchlist_id (FK → watchlist)`, `symbol`, `mos_alert_min`, `mos_alert_max`, `added_at` — individual symbol entries
- `Alert` JPA entity + `alert` table (V2): `id`, `user_id`, `alert_type (VARCHAR 50)`, `symbol`, `threshold`, `status`, `triggered_at`, `acknowledged_at`
- `AlertType` enum: `MOS_ENTRY`, `MOS_EXIT`, `PRICE_TARGET_HIT`, `FUNDAMENTAL_DEGRADE`, `DIVIDEND_CUT`, `INSIDER_SELL`, `EARNINGS_SURPRISE`, `REBALANCE_NEEDED`
- `AlertStatus` enum: `ACTIVE`, `ACKNOWLEDGED`, `DISMISSED`
- `WatchlistRepository` with `findByUser(User)` — returns `List<Watchlist>`
- `AlertRepository` with `findByUserAndStatus(User, AlertStatus)` and `findByUserOrderByTriggeredAtDesc(User)`
- Spring Security auth filter — `authentication.getName()` returns the authenticated user's email (email is the JWT subject, via `UserDetailsServiceImpl.loadUserByUsername(email)`)
- `UserRepository.findByEmail(String)` — resolves the `User` entity from an email
- Flyway migrations V1–V6 applied; next version is V7

### What this phase introduces

- Flyway migration `V7__watchlist_fundamental_degrade.sql` — adds `fundamental_degrade_threshold` column to `watchlist_item` and an index on `watchlist.user_id`
- Updated `WatchlistItem` entity with `fundamentalDegradeThreshold` field
- `WatchlistItemRepository` — three derived query methods for item lookup
- `WatchlistService` — business logic, user resolution, auto-creation of default `Watchlist` on first item add
- `WatchlistController` — 5 endpoints in package `it.mazzoni.vis.watchlist`
- DTOs: `AddWatchlistItemRequest`, `UpdateWatchlistThresholdRequest`, `WatchlistItemResponse`, `AlertResponse`
- Testcontainers PostgreSQL integration test `WatchlistIT`

---

## Decisions

### User-scoped data with ownership enforcement

All watchlist operations are scoped to the authenticated user resolved via `authentication.getName()` (email) → `userRepository.findByEmail()`. `PUT` and `DELETE` use `WatchlistItemRepository.findByIdAndWatchlist_User(id, user)` for ownership-safe lookup. If the item does not exist **or belongs to another user**, 404 is returned — not 403 — to avoid information disclosure about other users' watchlists.

### One watchlist per user — auto-created on first add

The `Watchlist` entity serves as a user-owned container. Each user has at most one watchlist, auto-created with name `"My Watchlist"` the first time they call `POST /api/v1/watchlist`. The API surface exposes `WatchlistItem` resources directly; the `Watchlist` container is an implementation detail not visible in responses. Multi-watchlist support (named lists) is deferred to a later phase.

### Duplicate symbol prevention

`POST /api/v1/watchlist` returns `409 Conflict` with message `"Symbol already in watchlist: {symbol}"` if the authenticated user already has that symbol in their watchlist. Symbol matching is case-insensitive: the stored symbol is always uppercased.

### Alerts — read-only in F1

`GET /api/v1/watchlist/alerts` returns only `ACTIVE` alerts from the `alert` table for the current user. Alert creation is G1's responsibility (nightly job). Alert acknowledgement (`PUT /api/v1/alerts/{id}/ack`) is G2's responsibility. No write operations on `alert` rows in this phase.

### fundamentalDegrade threshold stored but not yet evaluated

`fundamental_degrade_threshold` is persisted in `watchlist_item` and surfaced in all watchlist responses. No detection logic reads it in F1; the G1 alert detection job will reference this threshold when it evaluates fundamental degradation.

---

## Request / Response Shapes

### `GET /api/v1/watchlist`

```json
[
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "symbol": "AAPL",
    "mosAlertMin": 10.0,
    "mosAlertMax": 30.0,
    "fundamentalDegradeThreshold": null,
    "addedAt": "2026-06-20T10:15:30"
  },
  {
    "id": "7b8c9d1e-2f3a-4b5c-6d7e-8f9a0b1c2d3e",
    "symbol": "MSFT",
    "mosAlertMin": null,
    "mosAlertMax": null,
    "fundamentalDegradeThreshold": 70.0,
    "addedAt": "2026-06-20T11:00:00"
  }
]
```
Empty array `[]` if no watchlist items. Items ordered newest-first (`addedAt DESC`).

---

### `POST /api/v1/watchlist`

Request body:
```json
{
  "symbol": "AAPL",
  "mosAlertMin": 10.0,
  "mosAlertMax": 30.0,
  "fundamentalDegradeThreshold": null
}
```
- `symbol` is required and must be non-blank; all other fields are optional (nullable).
- Symbol is stored uppercased.

Response: `201 Created`
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "symbol": "AAPL",
  "mosAlertMin": 10.0,
  "mosAlertMax": 30.0,
  "fundamentalDegradeThreshold": null,
  "addedAt": "2026-06-20T10:15:30"
}
```

Error cases:
- `400 Bad Request` — blank `symbol`
- `409 Conflict` — symbol already in watchlist; body: `{ "error": "Symbol already in watchlist: AAPL" }`

---

### `PUT /api/v1/watchlist/{id}`

Request body:
```json
{
  "mosAlertMin": 15.0,
  "mosAlertMax": null,
  "fundamentalDegradeThreshold": 70.0
}
```
All fields nullable. A null value clears the existing threshold.

Response: `200 OK` with updated `WatchlistItemResponse`.

Error cases:
- `404 Not Found` — item not found or does not belong to authenticated user

---

### `DELETE /api/v1/watchlist/{id}`

Response: `204 No Content`

Error cases:
- `404 Not Found` — item not found or does not belong to authenticated user

---

### `GET /api/v1/watchlist/alerts`

```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "alertType": "MOS_ENTRY",
    "symbol": "AAPL",
    "threshold": 15.0,
    "triggeredAt": "2026-06-19T22:00:00"
  }
]
```
Only `status = ACTIVE` alerts are returned. Empty array `[]` if none. Ordered by `triggeredAt DESC`.

---

## Authorization

All 5 endpoints require authentication. Any role (ADMIN, ADVISOR, INVESTOR) is accepted. No endpoint is ADMIN-only.

| Endpoint | Required role |
|---|---|
| All `GET/POST/PUT/DELETE /api/v1/watchlist/**` | `hasAnyRole("ADMIN","ADVISOR","INVESTOR")` |

---

## Out of Scope

- Multi-watchlist support (named lists per user) — deferred; currently one watchlist per user
- `GET /api/v1/watchlist/{id}` — individual item fetch not needed; the list endpoint is sufficient
- Alert acknowledgement (`PUT /api/v1/alerts/{id}/ack`) — belongs to G2
- Alert creation / detection — belongs to G1 nightly job
- Redis caching of watchlist responses — future hardening pass
- No FMP or MarketDataClient calls — all data is user-managed (watchlist items) or pre-persisted (alerts)
- No MoS live recomputation at request time — MoS is recomputed by the G1 job, not by this endpoint
- No frontend changes in this phase — PFD1 (Group PFD) will wire up the full HTML demo
