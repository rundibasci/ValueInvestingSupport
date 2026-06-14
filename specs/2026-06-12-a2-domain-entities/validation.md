# Validation — Phase A2: Domain Entities & DB Schema

## Definition of Done

This branch is ready to merge when **all** of the following are true.

## 1. Flyway Migration

- [ ] `V2__core_schema.sql` applies without errors on a clean PostgreSQL database (run `docker compose up -d` then `./mvnw spring-boot:run -Dspring.profiles.active=local`)
- [ ] Flyway reports all migrations as `Success` in `flyway_schema_history`
- [ ] Re-running the app a second time produces no Flyway errors (idempotency of already-applied migration)
- [ ] No `NOT NULL` constraint is missing for any field marked not-null in requirements

## 2. Application Start

- [ ] `./mvnw spring-boot:run -Dspring.profiles.active=local` starts without errors
- [ ] `GET /actuator/health` returns `{"status":"UP"}`
- [ ] No `HibernateException` or schema validation errors in startup log

## 3. Repository & Entity Tests (@DataJpaTest)

- [ ] `SecurityRepositoryTest`: save + find-by-symbol round-trip passes; duplicate symbol triggers `DataIntegrityViolationException`
- [ ] `FundamentalSnapshotRepositoryTest`: save snapshot linked to security; `findBySecurityAndPeriod` returns correct record
- [ ] `UserRepositoryTest`: save user; find by email succeeds; saving a `User` with null `passwordHash` throws a constraint violation
- [ ] `PortfolioRepositoryTest`: save portfolio with holdings; `findByUser` returns the correct portfolio
- [ ] All `@DataJpaTest` tests pass with `./mvnw test`

## 4. Code Quality

- [ ] No Lombok annotations (`@Data`, `@Builder`, `@Getter`, etc.) in any entity class
- [ ] All entity IDs are `UUID`, not `Long`
- [ ] No hardcoded schema DDL in Java — all schema changes go through Flyway only
- [ ] `WatchlistItem` is either an `@Entity` with its own UUID PK or a properly mapped `@ElementCollection` — no orphan mapping

## 5. Review Checklist

- [ ] `V2__core_schema.sql` reviewed for: correct `NOT NULL`, proper FK constraints with `ON DELETE` clauses, indexes on all foreign keys and query-hot columns
- [ ] `PriceQuote` table is declaratively partitioned by `quote_date` range (not a plain table)
- [ ] No entity references a class from a future phase that doesn't exist yet
