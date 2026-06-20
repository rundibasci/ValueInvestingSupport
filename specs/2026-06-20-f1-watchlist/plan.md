# Plan — Group F1: Watchlist (M6 start)

## Task Group 1: Flyway Migration & Entity Update

1.1 Create migration `V7__watchlist_fundamental_degrade.sql`:
  ```sql
  ALTER TABLE watchlist_item
    ADD COLUMN fundamental_degrade_threshold DECIMAL(10,4);

  CREATE INDEX IF NOT EXISTS idx_watchlist_user
    ON watchlist(user_id);
  ```
  The `watchlist_item` and `watchlist` tables already exist from V2. This migration only adds the missing threshold column and a covering index on `watchlist.user_id`.

1.2 Update `WatchlistItem` entity in `it.mazzoni.vis.domain.entity`:
  - Add field: `@Column(precision = 10, scale = 4) private BigDecimal fundamentalDegradeThreshold;`
  - Add getter: `public BigDecimal getFundamentalDegradeThreshold() { return fundamentalDegradeThreshold; }`
  - Add setter: `public void setFundamentalDegradeThreshold(BigDecimal v) { this.fundamentalDegradeThreshold = v; }`

1.3 Add `findFirstByUser` to `WatchlistRepository` in `it.mazzoni.vis.domain.repository`:
  ```java
  Optional<Watchlist> findFirstByUser(User user);
  ```
  The existing `List<Watchlist> findByUser(User user)` is retained unchanged.

---

## Task Group 2: WatchlistItemRepository

2.1 Create `WatchlistItemRepository` in `it.mazzoni.vis.domain.repository` extending `JpaRepository<WatchlistItem, UUID>`:
  ```java
  List<WatchlistItem> findByWatchlist_UserOrderByAddedAtDesc(User user);
  Optional<WatchlistItem> findByIdAndWatchlist_User(UUID id, User user);
  Optional<WatchlistItem> findBySymbolAndWatchlist_User(String symbol, User user);
  ```
  - `findByWatchlist_UserOrderByAddedAtDesc` — lists all items for a user, newest first
  - `findByIdAndWatchlist_User` — ownership-safe lookup by ID (returns empty if item belongs to another user)
  - `findBySymbolAndWatchlist_User` — duplicate symbol check before insert

---

## Task Group 3: DTOs

3.1 Create `AddWatchlistItemRequest` record in `it.mazzoni.vis.watchlist.dto`:
  ```java
  public record AddWatchlistItemRequest(
      @NotBlank String symbol,
      BigDecimal mosAlertMin,
      BigDecimal mosAlertMax,
      BigDecimal fundamentalDegradeThreshold
  ) {}
  ```

3.2 Create `UpdateWatchlistThresholdRequest` record in `it.mazzoni.vis.watchlist.dto`:
  ```java
  public record UpdateWatchlistThresholdRequest(
      BigDecimal mosAlertMin,
      BigDecimal mosAlertMax,
      BigDecimal fundamentalDegradeThreshold
  ) {}
  ```
  All fields nullable — a null value means "clear this threshold."

3.3 Create `WatchlistItemResponse` record in `it.mazzoni.vis.watchlist.dto`:
  ```java
  public record WatchlistItemResponse(
      UUID id,
      String symbol,
      BigDecimal mosAlertMin,
      BigDecimal mosAlertMax,
      BigDecimal fundamentalDegradeThreshold,
      LocalDateTime addedAt
  ) {
      public static WatchlistItemResponse from(WatchlistItem item) { ... }
  }
  ```
  `from()` maps all fields directly from the entity.

3.4 Create `AlertResponse` record in `it.mazzoni.vis.watchlist.dto`:
  ```java
  public record AlertResponse(
      UUID id,
      String alertType,
      String symbol,
      BigDecimal threshold,
      LocalDateTime triggeredAt
  ) {
      public static AlertResponse from(Alert alert) { ... }
  }
  ```
  `alertType` is `alert.getAlertType().name()` (string form of the enum).

---

## Task Group 4: WatchlistService

4.1 Create `WatchlistService` in `it.mazzoni.vis.watchlist` annotated `@Service`:
  - Constructor-inject `WatchlistRepository`, `WatchlistItemRepository`, `UserRepository`, `AlertRepository`

4.2 Implement private helper `resolveUser(Authentication auth) → User`:
  ```java
  private User resolveUser(Authentication auth) {
      return userRepository.findByEmail(auth.getName())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  }
  ```

4.3 Implement private helper `getOrCreateWatchlist(User user) → Watchlist`:
  ```java
  private Watchlist getOrCreateWatchlist(User user) {
      return watchlistRepository.findFirstByUser(user)
          .orElseGet(() -> {
              Watchlist wl = new Watchlist();
              wl.setUser(user);
              wl.setName("My Watchlist");
              return watchlistRepository.save(wl);
          });
  }
  ```
  A user's first `POST /api/v1/watchlist` triggers auto-creation of their single `Watchlist` row.

4.4 Implement `list(Authentication auth) → List<WatchlistItemResponse>`:
  - Resolve user → load items via `watchlistItemRepository.findByWatchlist_UserOrderByAddedAtDesc(user)` → map to `WatchlistItemResponse`

4.5 Implement `add(Authentication auth, AddWatchlistItemRequest req) → WatchlistItemResponse`:
  - Resolve user → check duplicate via `watchlistItemRepository.findBySymbolAndWatchlist_User(req.symbol().toUpperCase(), user)` → throw `ResponseStatusException(CONFLICT, "Symbol already in watchlist: {symbol}")` if present
  - `getOrCreateWatchlist(user)` → build and save `WatchlistItem` (symbol uppercased) → return `WatchlistItemResponse.from(...)`

4.6 Implement `updateThresholds(Authentication auth, UUID id, UpdateWatchlistThresholdRequest req) → WatchlistItemResponse`:
  - Resolve user → `watchlistItemRepository.findByIdAndWatchlist_User(id, user)`.orElseThrow(404) → update three threshold fields → save → return `WatchlistItemResponse.from(...)`

4.7 Implement `remove(Authentication auth, UUID id)`:
  - Resolve user → `watchlistItemRepository.findByIdAndWatchlist_User(id, user)`.orElseThrow(404) → `watchlistItemRepository.delete(item)`

4.8 Implement `listActiveAlerts(Authentication auth) → List<AlertResponse>`:
  - Resolve user → `alertRepository.findByUserAndStatus(user, AlertStatus.ACTIVE)` → map to `AlertResponse`

---

## Task Group 5: WatchlistController

5.1 Create `WatchlistController` in `it.mazzoni.vis.watchlist` annotated `@RestController @RequestMapping("/api/v1/watchlist")`:
  - Constructor-inject `WatchlistService`
  - Security: all endpoints require `hasAnyRole("ADMIN","ADVISOR","INVESTOR")` (applied via Spring Security config or method-level annotation)

5.2 Map endpoints:
  ```java
  @GetMapping                          // GET /api/v1/watchlist
  public List<WatchlistItemResponse> list(Authentication auth)

  @PostMapping                         // POST /api/v1/watchlist
  @ResponseStatus(HttpStatus.CREATED)
  public WatchlistItemResponse add(Authentication auth, @Valid @RequestBody AddWatchlistItemRequest req)

  @PutMapping("/{id}")                 // PUT /api/v1/watchlist/{id}
  public WatchlistItemResponse updateThresholds(Authentication auth,
      @PathVariable UUID id,
      @RequestBody UpdateWatchlistThresholdRequest req)

  @DeleteMapping("/{id}")              // DELETE /api/v1/watchlist/{id}
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(Authentication auth, @PathVariable UUID id)

  @GetMapping("/alerts")               // GET /api/v1/watchlist/alerts
  public List<AlertResponse> listAlerts(Authentication auth)
  ```

5.3 Error mapping: `ResponseStatusException` propagates HTTP status automatically; no additional `@ExceptionHandler` needed in this controller.

---

## Task Group 6: Unit Tests

6.1 Create `WatchlistControllerTest` in `backend/src/test/java/.../watchlist/`:
  - `@WebMvcTest(WatchlistController.class)`
  - Mock `WatchlistService` with `@MockBean`
  - All requests carry a valid JWT via Spring Security test support (`@WithMockUser(roles = "INVESTOR")`)

  Test cases:
  - `GET /api/v1/watchlist` → 200; service returns list of two items; response is JSON array of size 2
  - `GET /api/v1/watchlist` → 200; service returns empty list; response is `[]`
  - `POST /api/v1/watchlist` with valid body → 201; response contains `symbol`, `id`, `addedAt`
  - `POST /api/v1/watchlist` with blank `symbol` → 400 (Bean Validation)
  - `POST /api/v1/watchlist` when service throws CONFLICT → 409 propagated
  - `PUT /api/v1/watchlist/{id}` with valid body → 200; response contains updated thresholds
  - `PUT /api/v1/watchlist/{id}` when service throws 404 → 404 propagated
  - `DELETE /api/v1/watchlist/{id}` → 204 No Content
  - `DELETE /api/v1/watchlist/{id}` when service throws 404 → 404 propagated
  - `GET /api/v1/watchlist/alerts` → 200; service returns one alert; response contains `alertType`, `symbol`
  - `GET /api/v1/watchlist/alerts` → 200; service returns empty list; response is `[]`
  - Unauthenticated request to any endpoint → 401

---

## Task Group 7: Integration Test (Testcontainers PostgreSQL)

7.1 Create `WatchlistIT` in `backend/src/test/java/.../watchlist/`:
  - `@SpringBootTest(webEnvironment = RANDOM_PORT)`
  - `@ActiveProfiles({"test", "watchlist-test"})`
  - `@Testcontainers` + `@Container static PostgreSQLContainer<?> postgres`
  - `@DynamicPropertySource` → sets `spring.datasource.url/username/password`
  - Obtain JWT: `POST /auth/login` with `admin` / `admin` credentials from the `DemoDataSeeder`

  Test cases (all requests carry `Authorization: Bearer <jwt>`):
  - `GET /api/v1/watchlist` → 200; response is `[]` (no items yet)
  - `POST /api/v1/watchlist` body `{ "symbol": "AAPL" }` → 201; `id` non-null; `symbol = "AAPL"`; `addedAt` non-null
  - `POST /api/v1/watchlist` body `{ "symbol": "AAPL" }` again → 409 (duplicate)
  - `GET /api/v1/watchlist` → 200; array size = 1; `symbol = "AAPL"`
  - `PUT /api/v1/watchlist/{id}` body `{ "mosAlertMin": 10.0, "mosAlertMax": 25.0 }` → 200; `mosAlertMin = 10.0`; `mosAlertMax = 25.0`
  - `POST /api/v1/watchlist` body `{ "symbol": "MSFT", "fundamentalDegradeThreshold": 70.0 }` → 201; `fundamentalDegradeThreshold = 70.0`
  - `GET /api/v1/watchlist` → 200; array size = 2
  - `DELETE /api/v1/watchlist/{aapl_id}` → 204
  - `GET /api/v1/watchlist` → 200; array size = 1; only MSFT remains
  - Seed an `Alert` row via `JdbcTemplate` (`status=ACTIVE`, `alert_type=MOS_ENTRY`, `symbol=MSFT`) for the admin user
  - `GET /api/v1/watchlist/alerts` → 200; array size = 1; `alertType = "MOS_ENTRY"`; `symbol = "MSFT"`
  - Unauthenticated `GET /api/v1/watchlist` → 401

7.2 Create `application-watchlist-test.yml` in `backend/src/test/resources/`:
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
8.2 Run `WatchlistIT`: `mvn test -pl backend -Dtest=WatchlistIT`
8.3 Manual smoke test curl sequence (see validation.md)
8.4 Verify Flyway migration V7 applies cleanly: `mvn flyway:migrate -pl backend`
8.5 Merge `phase/group-f1-watchlist` → `main` via `/merge-phase`
