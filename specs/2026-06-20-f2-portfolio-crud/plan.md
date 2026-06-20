# Plan — Group F2: Portfolio CRUD & Holdings (M6)

## Task Group 1: Flyway Migration — Portfolio Index

1.1 Create migration `V8__portfolio_holding_index.sql`:
  ```sql
  CREATE INDEX IF NOT EXISTS idx_holding_portfolio
    ON holding(portfolio_id);

  CREATE INDEX IF NOT EXISTS idx_holding_portfolio_symbol
    ON holding(portfolio_id, symbol);
  ```
  The `portfolio` and `holding` tables exist from V2. This migration only adds the missing lookup indexes not included in the original schema.

---

## Task Group 2: Repository Additions

2.1 Add to `PortfolioRepository` in `it.mazzoni.vis.domain.repository`:
  ```java
  Optional<Portfolio> findByIdAndUser(UUID id, User user);
  ```
  Used for all operations that must verify the authenticated user owns the portfolio before accessing it. Returns `Optional.empty()` if the portfolio belongs to another user — never 403, always 404 (information hiding).

2.2 Add to `HoldingRepository` in `it.mazzoni.vis.domain.repository`:
  ```java
  List<Holding> findByPortfolioOrderByAddedAtDesc(Portfolio portfolio);
  Optional<Holding> findByIdAndPortfolio(UUID id, Portfolio portfolio);
  ```
  - `findByPortfolioOrderByAddedAtDesc` — returns all lots for a portfolio, newest first
  - `findByIdAndPortfolio` — ownership-safe holding lookup (portfolio already verified to belong to the user via 2.1; this guards against cross-portfolio access)

---

## Task Group 3: DTOs

3.1 Create `CreatePortfolioRequest` record in `it.mazzoni.vis.portfolio.dto`:
  ```java
  public record CreatePortfolioRequest(
      @NotBlank String name,
      String description
  ) {}
  ```

3.2 Create `PortfolioSummaryResponse` record in `it.mazzoni.vis.portfolio.dto`:
  ```java
  public record PortfolioSummaryResponse(
      UUID id,
      String name,
      String description,
      int holdingCount,
      LocalDateTime createdAt,
      LocalDateTime updatedAt
  ) {
      public static PortfolioSummaryResponse from(Portfolio p) { ... }
  }
  ```
  `holdingCount` = `p.getHoldings().size()`.

3.3 Create `AddHoldingRequest` record in `it.mazzoni.vis.portfolio.dto`:
  ```java
  public record AddHoldingRequest(
      @NotBlank String symbol,
      @NotNull @Positive BigDecimal quantity,
      BigDecimal averageCostBasis,
      String currency
  ) {}
  ```
  Multiple lots of the same symbol are permitted (no duplicate guard). Symbol is stored uppercased.

3.4 Create `UpdateHoldingRequest` record in `it.mazzoni.vis.portfolio.dto`:
  ```java
  public record UpdateHoldingRequest(
      @NotNull @Positive BigDecimal quantity,
      BigDecimal averageCostBasis,
      String currency
  ) {}
  ```
  All fields except `quantity` are nullable. A null value clears the existing field.

3.5 Create `HoldingDetailItem` record in `it.mazzoni.vis.portfolio.dto`:
  ```java
  public record HoldingDetailItem(
      UUID id,
      String symbol,
      BigDecimal quantity,
      BigDecimal averageCostBasis,
      String currency,
      BigDecimal currentPrice,
      BigDecimal currentValue,
      BigDecimal weightPercent,
      BigDecimal compositeFairValue,
      BigDecimal marginOfSafety,
      String recommendation,
      LocalDateTime addedAt
  ) {}
  ```
  `currentPrice`, `currentValue`, `weightPercent`, `compositeFairValue`, `marginOfSafety`, `recommendation` are all nullable (symbol not yet ingested or no valuation result).

3.6 Create `PortfolioDetailResponse` record in `it.mazzoni.vis.portfolio.dto`:
  ```java
  public record PortfolioDetailResponse(
      UUID id,
      String name,
      String description,
      BigDecimal totalValue,
      BigDecimal weightedMoS,
      List<HoldingDetailItem> holdings,
      LocalDateTime createdAt,
      LocalDateTime updatedAt
  ) {}
  ```
  `totalValue` = sum of `holding.quantity × currentPrice` for holdings with a known price; null if no prices are available.
  `weightedMoS` = sum of `(weightPercent / 100) × marginOfSafety` for holdings where both values are non-null; null if no valued holdings.

---

## Task Group 4: PortfolioService

4.1 Create `PortfolioService` in `it.mazzoni.vis.portfolio` annotated `@Service`:
  - Constructor-inject `PortfolioRepository`, `HoldingRepository`, `UserRepository`, `SecurityRepository`, `PriceQuoteRepository`, `ValuationResultRepository`

4.2 Implement private helper `resolveUser(Authentication auth) → User`:
  ```java
  private User resolveUser(Authentication auth) {
      return userRepository.findByEmail(auth.getName())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  }
  ```

4.3 Implement private helper `resolvePortfolio(UUID id, User user) → Portfolio`:
  ```java
  private Portfolio resolvePortfolio(UUID id, User user) {
      return portfolioRepository.findByIdAndUser(id, user)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
  ```

4.4 Implement `listPortfolios(Authentication auth) → List<PortfolioSummaryResponse>`:
  - `resolveUser` → `portfolioRepository.findByUserOrderByCreatedAtDesc(user)` → map each `Portfolio` to `PortfolioSummaryResponse.from(p)`.
  - Note: `Portfolio.getHoldings()` is `LAZY`; calling `.size()` triggers a secondary load per portfolio. Acceptable for MVP (portfolio count per user is small); a `@Query` with `COUNT` can replace it if N+1 becomes a concern.

4.5 Implement `createPortfolio(Authentication auth, CreatePortfolioRequest req) → PortfolioSummaryResponse`:
  - `resolveUser` → build `Portfolio` (name, description, user) → `portfolioRepository.save(p)` → return `PortfolioSummaryResponse.from(p)`.

4.6 Implement `getPortfolioDetail(Authentication auth, UUID id) → PortfolioDetailResponse`:
  - `resolveUser` → `resolvePortfolio(id, user)` → `holdingRepository.findByPortfolioOrderByAddedAtDesc(portfolio)`.
  - For each holding symbol (deduplicated for DB lookup):
    - `securityRepository.findBySymbol(symbol)` → if present, `priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)` → get `close` price.
    - `securityRepository.findBySymbol(symbol)` → if present, `valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security)` → get `compositeFairValue`, `marginOfSafety`, `recommendation`.
  - Compute `currentValue = quantity × currentPrice` per holding (null if price absent).
  - Compute `totalValue = sum(currentValue)` across all holdings (null if all prices absent).
  - Compute `weightPercent = (currentValue / totalValue) × 100` per holding (null if `totalValue` is null or zero).
  - Compute `weightedMoS = sum(weightPercent / 100 × marginOfSafety)` for holdings where both are non-null (null if none qualify).
  - Build `HoldingDetailItem` per holding → build and return `PortfolioDetailResponse`.

4.7 Implement `addHolding(Authentication auth, UUID portfolioId, AddHoldingRequest req) → HoldingDetailItem`:
  - `resolveUser` → `resolvePortfolio(portfolioId, user)` → build `Holding` (symbol uppercased, quantity, averageCostBasis, currency, portfolio) → `holdingRepository.save(h)`.
  - Enrich with price/valuation (same lookup as 4.6 for a single symbol) → return `HoldingDetailItem`.

4.8 Implement `updateHolding(Authentication auth, UUID portfolioId, UUID holdingId, UpdateHoldingRequest req) → HoldingDetailItem`:
  - `resolveUser` → `resolvePortfolio(portfolioId, user)` → `holdingRepository.findByIdAndPortfolio(holdingId, portfolio)`.orElseThrow(404) → update `quantity`, `averageCostBasis`, `currency` → `holdingRepository.save(h)`.
  - Enrich and return `HoldingDetailItem`.

4.9 Implement `removeHolding(Authentication auth, UUID portfolioId, UUID holdingId)`:
  - `resolveUser` → `resolvePortfolio(portfolioId, user)` → `holdingRepository.findByIdAndPortfolio(holdingId, portfolio)`.orElseThrow(404) → `holdingRepository.delete(h)`.

---

## Task Group 5: PortfolioController

5.1 Create `PortfolioController` in `it.mazzoni.vis.portfolio` annotated `@RestController @RequestMapping("/api/v1/portfolios")`:
  - Constructor-inject `PortfolioService`.
  - Security: all endpoints require `hasAnyRole("ADMIN","ADVISOR","INVESTOR")`.

5.2 Map endpoints:
  ```java
  @GetMapping                              // GET /api/v1/portfolios
  public List<PortfolioSummaryResponse> list(Authentication auth)

  @PostMapping                             // POST /api/v1/portfolios
  @ResponseStatus(HttpStatus.CREATED)
  public PortfolioSummaryResponse create(Authentication auth,
      @Valid @RequestBody CreatePortfolioRequest req)

  @GetMapping("/{id}")                     // GET /api/v1/portfolios/{id}
  public PortfolioDetailResponse detail(Authentication auth,
      @PathVariable UUID id)

  @PostMapping("/{id}/holdings")           // POST /api/v1/portfolios/{id}/holdings
  @ResponseStatus(HttpStatus.CREATED)
  public HoldingDetailItem addHolding(Authentication auth,
      @PathVariable UUID id,
      @Valid @RequestBody AddHoldingRequest req)

  @PutMapping("/{id}/holdings/{holdingId}") // PUT /api/v1/portfolios/{id}/holdings/{holdingId}
  public HoldingDetailItem updateHolding(Authentication auth,
      @PathVariable UUID id,
      @PathVariable UUID holdingId,
      @Valid @RequestBody UpdateHoldingRequest req)

  @DeleteMapping("/{id}/holdings/{holdingId}") // DELETE /api/v1/portfolios/{id}/holdings/{holdingId}
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeHolding(Authentication auth,
      @PathVariable UUID id,
      @PathVariable UUID holdingId)
  ```

5.3 Error propagation: `ResponseStatusException` propagates HTTP status automatically; no `@ExceptionHandler` needed in this controller.

---

## Task Group 6: Unit Tests

6.1 Create `PortfolioControllerTest` in `backend/src/test/java/.../portfolio/`:
  - `@WebMvcTest(PortfolioController.class)`
  - Mock `PortfolioService` with `@MockBean`
  - All requests carry a valid JWT via `@WithMockUser(roles = "INVESTOR")`

  Test cases:
  - `GET /api/v1/portfolios` → 200; service returns list of two summaries; response is JSON array of size 2
  - `GET /api/v1/portfolios` → 200; service returns empty list; response is `[]`
  - `POST /api/v1/portfolios` with valid body `{ "name": "My Portfolio" }` → 201; response contains `id`, `name`, `holdingCount = 0`
  - `POST /api/v1/portfolios` with blank `name` → 400 (Bean Validation)
  - `GET /api/v1/portfolios/{id}` → 200; response contains `holdings`, `totalValue`, `weightedMoS`
  - `GET /api/v1/portfolios/{id}` when service throws 404 → 404
  - `POST /api/v1/portfolios/{id}/holdings` with valid body → 201; response contains `id`, `symbol`, `quantity`
  - `POST /api/v1/portfolios/{id}/holdings` with blank `symbol` → 400
  - `POST /api/v1/portfolios/{id}/holdings` with null `quantity` → 400
  - `PUT /api/v1/portfolios/{id}/holdings/{holdingId}` with valid body → 200; response contains updated `quantity`
  - `PUT /api/v1/portfolios/{id}/holdings/{holdingId}` when service throws 404 → 404
  - `DELETE /api/v1/portfolios/{id}/holdings/{holdingId}` → 204 No Content
  - `DELETE /api/v1/portfolios/{id}/holdings/{holdingId}` when service throws 404 → 404
  - Unauthenticated request to any endpoint → 401

---

## Task Group 7: Integration Test (Testcontainers PostgreSQL)

7.1 Create `PortfolioIT` in `backend/src/test/java/.../portfolio/`:
  - `@SpringBootTest(webEnvironment = RANDOM_PORT)`
  - `@ActiveProfiles({"test", "portfolio-test"})`
  - `@Testcontainers` + `@Container static PostgreSQLContainer<?> postgres`
  - `@DynamicPropertySource` → sets `spring.datasource.url/username/password`
  - Obtain JWT: `POST /auth/login` with `admin` / `admin` credentials from `DemoDataSeeder`

  Test cases (all requests carry `Authorization: Bearer <jwt>`):
  - `GET /api/v1/portfolios` → 200; response is `[]` (no portfolios yet)
  - `POST /api/v1/portfolios` body `{ "name": "Growth Portfolio", "description": "Long-term holds" }` → 201; `id` non-null; `name = "Growth Portfolio"`; `holdingCount = 0`; save `portfolioId`
  - `GET /api/v1/portfolios` → 200; array size = 1; `name = "Growth Portfolio"`
  - `POST /api/v1/portfolios/{portfolioId}/holdings` body `{ "symbol": "aapl", "quantity": 10, "averageCostBasis": 150.00, "currency": "USD" }` → 201; `symbol = "AAPL"` (uppercased); `quantity = 10`; `currentPrice = null` (AAPL not in security table); save `holdingId1`
  - `POST /api/v1/portfolios/{portfolioId}/holdings` body `{ "symbol": "MSFT", "quantity": 5 }` → 201; `symbol = "MSFT"`; save `holdingId2`
  - `GET /api/v1/portfolios/{portfolioId}` → 200; `holdings` array size = 2; `totalValue = null` (no price quotes); `weightedMoS = null`
  - Seed security + price via `JdbcTemplate`: insert `security` row (`symbol=AAPL`), `price_quote` row for AAPL (`close=180.00`), and `valuation_result` row (`composite_fair_value=210.00, margin_of_safety=16.67, recommendation=QUALITY_VALUE`)
  - `GET /api/v1/portfolios/{portfolioId}` → 200; AAPL `HoldingDetailItem`: `currentPrice = 180.00`; `currentValue = 1800.00`; `compositeFairValue = 210.00`; `marginOfSafety = 16.67`; MSFT: `currentPrice = null` (no quote seeded); `totalValue = 1800.00` (only AAPL valued); AAPL `weightPercent = 100.00`; `weightedMoS = 16.67`
  - `PUT /api/v1/portfolios/{portfolioId}/holdings/{holdingId1}` body `{ "quantity": 15, "averageCostBasis": 145.00, "currency": "USD" }` → 200; `quantity = 15`; `averageCostBasis = 145.00`
  - `DELETE /api/v1/portfolios/{portfolioId}/holdings/{holdingId2}` → 204
  - `GET /api/v1/portfolios/{portfolioId}` → 200; `holdings` array size = 1; only AAPL remains
  - `POST /api/v1/portfolios` body `{ "name": "Dividend Portfolio" }` → 201; second portfolio created
  - `GET /api/v1/portfolios` → 200; array size = 2
  - Unauthenticated `GET /api/v1/portfolios` → 401
  - `GET /api/v1/portfolios/{unknown_uuid}` → 404
  - `DELETE /api/v1/portfolios/{portfolioId}/holdings/{unknown_uuid}` → 404

7.2 Create `application-portfolio-test.yml` in `backend/src/test/resources/`:
  ```yaml
  spring:
    jpa:
      show-sql: false
    flyway:
      enabled: true
  ```

---

## Task Group 8: Review & Merge Readiness

8.1 Run all unit tests: `mvn test -pl backend`
8.2 Run `PortfolioIT`: `mvn test -pl backend -Dtest=PortfolioIT`
8.3 Manual smoke test curl sequence (see validation.md)
8.4 Verify Flyway migration V8 applies cleanly: `mvn flyway:migrate -pl backend`
8.5 Merge `phase/group-f2-portfolio-crud` → `main` via `/merge-phase`
