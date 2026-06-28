# Plan — J1: Google OpenID Connect Backend

## Group 1 — Flyway Migration & OAuthIdentity Entity

1.1 Create Flyway migration `V<next>__add_oauth_identity.sql`:
   - `oauth_identity` table: `id` (BIGINT PK), `user_id` (FK → `users.id`, NOT NULL), `provider` (VARCHAR, NOT NULL), `provider_subject` (VARCHAR, NOT NULL), `provider_email` (VARCHAR), `created_at` (TIMESTAMP)
   - Unique constraint on `(provider, provider_subject)`
   - Index on `provider_email`
   - H2-compatible DDL (no PostgreSQL-only syntax without guard)

1.2 Create `OAuthIdentity` JPA entity (Java record or class) mapping the `oauth_identity` table; bidirectional or unidirectional relationship to `User`

1.3 Create `OAuthIdentityRepository` with finder methods:
   - `findByProviderAndProviderSubject(String provider, String providerSubject)`
   - `findByProviderAndProviderEmail(String provider, String providerEmail)`

---

## Group 2 — Google OAuth2 Client Configuration

2.1 Add Spring Security OAuth2 Client dependency to `pom.xml` (`spring-boot-starter-oauth2-client`)

2.2 Create `GoogleOAuthProperties` (`@ConfigurationProperties` or `@Value`-based) loading `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and `GOOGLE_REDIRECT_URI` from environment variables

2.3 Configure Spring Security OAuth2 Client registration for Google in `application.yml` (or programmatic `ClientRegistrationRepository` bean):
   - Registration ID: `google`
   - Scopes: `openid`, `email`, `profile`
   - Redirect URI from env var
   - Make the registration conditional/optional so the app starts without Google credentials (demo/test profiles)

2.4 Update `SecurityConfig` to add OAuth2 Login support alongside the existing JWT filter chain:
   - `GET /oauth2/authorization/google` and `/login/oauth2/code/google` are public paths
   - Existing `/auth/**` permit-all and `/api/**` JWT-protected rules unchanged
   - OAuth2 login success handler delegates to custom logic (Group 3)

---

## Group 3 — Account Resolution & Linking Logic

3.1 Create `GoogleOAuthUserService` (or `OidcUserService` customization):
   - Extract `sub`, `email`, `email_verified`, `name` from the OIDC ID token / `OidcUser`
   - Reject if `email_verified` is not `true`

3.2 Create `OAuthAccountResolver` service with the core linking logic:
   - Look up `OAuthIdentity` by `(GOOGLE, sub)`
   - If found: return the linked `User`
   - If not found: look up `User` by verified email
     - If email match: create `OAuthIdentity` linking Google identity to existing user; return user
     - If no match: create new `User` with role `INVESTOR`, create `OAuthIdentity`; return user
   - Never change an existing user's role during linking

3.3 Unit tests for `OAuthAccountResolver`:
   - New Google user (no existing email) → creates `INVESTOR` user + `OAuthIdentity`
   - Existing password user with same email → links without creating duplicate user
   - Repeat login with existing `OAuthIdentity` → returns same user, no new records
   - `email_verified=false` → rejected
   - Existing admin user with same email → links but role stays `ADMIN` (not downgraded)

---

## Group 4 — Token Issuance & Handoff After Google Auth

4.1 Create `OAuthLoginSuccessHandler` implementing `AuthenticationSuccessHandler`:
   - Calls `OAuthAccountResolver.resolve()` to get/create the `User`
   - Calls `JwtService.issueAccessToken()` and `JwtService.issueRefreshToken()`
   - Generates a short-lived, single-use handoff code (UUID, stored in Redis with 60s TTL, value = access token)
   - Sets the refresh token as an httpOnly cookie (matching existing pattern)
   - Redirects to the frontend callback URL with only the handoff code as a query parameter (e.g., `?code=<uuid>`)

4.2 Create `GET /auth/oauth2/token` endpoint:
   - Accepts `code` query parameter
   - Looks up the handoff code in Redis; if found, returns the access token and deletes the code
   - If not found or expired: returns 401
   - This is the exchange endpoint the frontend will call in J2

4.3 Update `.env.example` with `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` placeholders and setup guidance comments

---

## Group 5 — Spring Security Integration & Demo Profile Safety

5.1 Ensure `SecurityConfig` correctly chains OAuth2 login and JWT filter without conflict:
   - OAuth2 login flow handles the `/oauth2/authorization/google` → callback → success handler path
   - JWT filter continues to protect `/api/**` endpoints
   - Both authentication paths coexist

5.2 Add conditional configuration so the app starts cleanly without Google credentials:
   - Demo profile (`application-demo.yml`): OAuth2 client registration disabled or optional
   - Test profile: OAuth2 client registration disabled unless explicitly enabled
   - If `GOOGLE_CLIENT_ID` is absent, the `/oauth2/authorization/google` path returns 404 or a descriptive error, not a startup failure

5.3 Verify existing auth flows are unaffected:
   - `POST /auth/login` still works
   - `POST /auth/refresh` still works
   - `POST /auth/logout` still works
   - Demo profile with H2 + no Google credentials starts without error

---

## Group 6 — Integration Tests

6.1 Create `GoogleOAuthIntegrationTest` (`@SpringBootTest`, `@AutoConfigureMockMvc`):
   - Mock the OIDC provider/JWK set using `MockOAuth2AuthorizationServer` or WireMock
   - Valid Google login → callback → handoff code issued → exchange returns access token
   - Invalid issuer → rejected
   - Invalid audience → rejected
   - Expired ID token → rejected
   - Invalid state/nonce → rejected
   - `email_verified=false` → rejected
   - Callback paths do not weaken `/api/**` JWT authorization

6.2 Create `OAuthAccountLinkingIntegrationTest`:
   - New Google user → `User` created with `INVESTOR` role + `OAuthIdentity` persisted
   - Existing password user with same email → `OAuthIdentity` created, same `User` record, no role change
   - Repeat Google login → same user, no duplicate `OAuthIdentity`
   - Conflicting provider subject → appropriate error

6.3 Verify existing auth tests still pass — no regressions in `AuthIntegrationTest`

---

## Group 7 — Manual Demo Verification

7.1 Document a manual test sequence for the localstack/demo profile:
   - Configure real Google OAuth2 credentials in `.env`
   - Start the app with demo profile
   - Navigate to `http://localhost:8080/oauth2/authorization/google` in a browser
   - Complete Google sign-in
   - Verify redirect to frontend callback with handoff code
   - Exchange handoff code via `GET /auth/oauth2/token?code=<code>`
   - Verify access token is valid (call a protected endpoint)
   - Verify `User` and `OAuthIdentity` records in the database
   - Verify existing password login still works alongside Google login
