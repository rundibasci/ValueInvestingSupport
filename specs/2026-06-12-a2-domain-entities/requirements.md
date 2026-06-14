# Requirements — Phase A2: Domain Entities & DB Schema

## Context

Builds directly on **A1** (Spring Boot 3.x scaffold, Maven, Java 21, Flyway configured with `V1__init.sql`, Docker Compose for PostgreSQL + Redis, `local`/`test`/`prod` profiles).

Phase A3 (Authentication) depends on the `User` entity. Group B (Data Pipeline) depends on `Security`, `FundamentalSnapshot`, `RatioSnapshot`, and `PriceQuote`. All entities must land in this phase so downstream phases can import them without circular dependency on partial work.

## Scope

Deliver all JPA entities, the Flyway `V2__core_schema.sql` migration, and Spring Data repositories in one phase.

### Entity groups

**Securities & Fundamentals**
- `Security` — company profile (symbol, name, exchange, sector, country, currency, market cap)
- `FundamentalSnapshot` — annual/quarterly income, balance sheet, cash flow snapshot linked to `Security`
- `RatioSnapshot` — computed ratios (PE, ROIC, ROE, debt-to-equity, etc.) linked to `Security`
- `PriceQuote` — OHLCV + adjusted close linked to `Security`; table partitioned by month

**Valuation & Scoring**
- `ValuationResult` — DCF/Graham/DDM fair values + composite + MoS, linked to `Security`
- `ValueScore` — five sub-scores + total, linked to `Security`
- `DividendRecord` — dividend payments history linked to `Security`
- `InsiderTrade` — insider transaction records linked to `Security`

**Users & Portfolio**
- `User` — email, hashed password, role (`ADVISOR` / `INVESTOR` / `ADMIN`), active flag
- `Portfolio` — name, description, owner (`User`)
- `Holding` — symbol, quantity, cost basis, linked to `Portfolio`
- `Watchlist` — name, owner (`User`), collection of watchlist items
- `Alert` — type, symbol, threshold, status, owner (`User`)

## Decisions

| Decision | Value | Reason |
|---|---|---|
| Primary key type | `UUID` (generated) | Avoids sequential ID leakage, portable across environments |
| No Lombok | Java 21 plain JPA classes (getters/setters) or records where JPA allows | Keep entities readable without annotation magic |
| Strict NOT NULL in SQL | All non-nullable fields enforced via `NOT NULL` in `V2__core_schema.sql` | DB is the last line of defence; JPA validation alone is insufficient |
| `PriceQuote` partitioning | Range partitioned by `quote_date` month (PostgreSQL declarative partitioning) | Time-series query performance; aligns with roadmap spec |
| No extra conventions | Follow naming and structure established in A1 | Consistent package layout: `domain/entity/`, `domain/repository/` |

## Out of Scope

- No service layer, no REST endpoints (those belong to later phases)
- No seed data or test fixtures (handled in test setup per phase)
- No custom queries beyond default Spring Data methods (added as needed per feature phase)
