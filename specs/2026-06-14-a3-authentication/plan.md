# Plan — A3: Authentication

## Group 1 — JWT Infrastructure

1.1 Add `jjwt` dependency to `pom.xml` (io.jsonwebtoken, version 0.12.x)

1.2 Create `JwtProperties` (`@ConfigurationProperties(prefix = "jwt")`) loading `privateKey` and `publicKey` from env vars; register as a `@Bean`

1.3 Create `JwtService`:
   - `issueAccessToken(username, roles) → String` — RS256 signed, 15-min expiry, claims: `sub`, `roles`, `jti`
   - `issueRefreshToken(username) → String` — UUID v4 token ID; store `refresh:{tokenId} → username` in Redis with 7-day TTL; return the UUID as the token value
   - `validateAccessToken(token) → Claims` — throws `JwtException` on invalid/expired
   - `validateRefreshToken(tokenId) → Optional<String>` — looks up Redis; returns username if present

1.4 Add `UserRepository.findByUsername(String username)` to the existing repository (A2 entity already exists)

---

## Group 2 — Spring Security Filter Chain

2.1 Create `UserDetailsServiceImpl` — loads `User` by username from `UserRepository`, maps `UserRole` → `GrantedAuthority`

2.2 Create `JwtAuthenticationFilter extends OncePerRequestFilter`:
   - Extract `Authorization: Bearer <token>` header
   - Call `JwtService.validateAccessToken()`
   - On success: set `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`
   - On failure: continue filter chain without authentication (401 is returned downstream by the filter chain, not here)

2.3 Create `SecurityConfig extends WebSecurityConfigurerAdapter` (or `@Bean SecurityFilterChain`):
   - `permitAll` on `/auth/**` and `/actuator/health`
   - `authenticated` on `/api/**`
   - `hasRole(ADMIN)` on `/api/v1/admin/**`
   - Add `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`
   - Disable CSRF (stateless API), disable session creation (`STATELESS`)
   - Wire `BCryptPasswordEncoder` bean
   - Wire `AuthenticationManager` with `UserDetailsServiceImpl` + `BCryptPasswordEncoder`

---

## Group 3 — Auth Endpoints

3.1 Create `AuthController` mapped to `/auth`:

   **POST /auth/login**
   - Request: `{ "username": "...", "password": "..." }`
   - Authenticate via `AuthenticationManager.authenticate()`
   - On success: call `JwtService.issueAccessToken()` + `JwtService.issueRefreshToken()`
   - Response 200: `{ "accessToken": "...", "refreshToken": "...", "expiresIn": 900 }`
   - On failure: 401 `{ "error": "Invalid credentials" }`

   **POST /auth/refresh**
   - Request: `{ "refreshToken": "..." }`
   - Call `JwtService.validateRefreshToken(tokenId)`
   - On success: issue new access token; optionally rotate refresh token
   - Response 200: `{ "accessToken": "...", "expiresIn": 900 }`
   - On failure: 401

   **POST /auth/logout**
   - Requires valid access token (authenticated endpoint)
   - Extract refresh token ID from request body: `{ "refreshToken": "..." }`
   - Delete `refresh:{tokenId}` from Redis
   - Response 204 No Content

3.2 Create request/response DTOs (Java records): `LoginRequest`, `LoginResponse`, `RefreshRequest`, `RefreshResponse`, `LogoutRequest`

3.3 Create `AuthException` (extends `RuntimeException`) + `@ControllerAdvice` handler returning `ProblemDetail` (RFC 7807)

---

## Group 4 — Admin User Endpoint + Integration Tests

4.1 Create `AdminUserController` mapped to `/api/v1/admin/users`:

   **POST /api/v1/admin/users** (requires ROLE_ADMIN)
   - Request: `{ "username": "...", "password": "...", "role": "INVESTOR|ADVISOR|ADMIN" }`
   - Validate: username unique, password min 8 chars
   - Encode password with `BCryptPasswordEncoder`, persist via `UserRepository`
   - Response 201: `{ "id": "...", "username": "...", "role": "..." }`
   - On duplicate username: 409

4.2 Create `CreateUserRequest` record + `UserResponse` record

4.3 Write integration tests (`@SpringBootTest`, `@AutoConfigureMockMvc`, embedded Redis via Testcontainers or `EmbeddedRedis`):
   - `POST /auth/login` valid credentials → 200, tokens present
   - `POST /auth/login` wrong password → 401
   - `GET /api/v1/ping` no token → 401
   - `GET /api/v1/ping` expired token → 401
   - `GET /api/v1/ping` valid token → 200
   - `POST /auth/refresh` valid refresh token → 200, new access token
   - `POST /auth/logout` → 204; subsequent `POST /auth/refresh` with same token → 401
   - `POST /api/v1/admin/users` as INVESTOR → 403

4.4 Add `GET /api/v1/ping` stub endpoint (returns 200 `{ "status": "ok" }`) to serve as the protected-endpoint smoke test — can be removed later or repurposed
