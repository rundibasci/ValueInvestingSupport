# Requirements — A3: Authentication

## Scope

Implement JWT RS256 authentication for the Spring Boot backend. After this phase every endpoint under `/api/**` requires a valid access token; `/auth/**` remains public. A3 is the final piece of M1 (Backend Running).

## What's in scope

- `POST /auth/login` — validate credentials, issue access + refresh tokens
- `POST /auth/refresh` — validate refresh token, issue new access token
- `POST /auth/logout` — invalidate refresh token (Redis delete)
- `POST /api/v1/admin/users` — admin-only endpoint to create new user accounts
- Spring Security filter chain wired to JWT validation
- `JwtService` (issue + validate RS256 tokens)
- `UserDetailsService` backed by the `User` JPA entity from A2

## What's out of scope

- Self-registration (no public sign-up endpoint)
- Password reset / email verification
- OAuth2 / social login
- Frontend login UI (Group H)

## Decisions

| Decision | Choice | Reason |
|---|---|---|
| Token algorithm | RS256 | Public key can be shared with downstream services without exposing the signing key |
| Access token TTL | 15 minutes | Short-lived; limits exposure if intercepted |
| Refresh token TTL | 7 days | Reasonable session length for an investment tool |
| Refresh token storage | Redis (key: `refresh:{tokenId}`, value: username, TTL: 7d) | Instant revocation on logout; survives restarts because Redis persists the token, not just in-memory |
| Token transport | Both tokens returned as JSON body fields | Simpler for the API-first approach; frontend stores in memory + secure storage |
| User creation | ROLE_ADMIN only via `POST /api/v1/admin/users` | MVP has no public sign-up; admins provision accounts |
| Key source | `JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY` env vars (PEM format, loaded with `@Value`) | Keys never committed; env var injection matches existing pattern |

## Key env vars (additions for A3)

```
JWT_PRIVATE_KEY    RS256 private key in PEM format
JWT_PUBLIC_KEY     RS256 public key in PEM format
```

Redis connection is already configured from A1 (`REDIS_HOST`, `REDIS_PORT`).

## Dependency on A2

`User` and `UserRole` JPA entities are already created. `UserRepository.findByUsername()` must be added (or confirmed present) for `UserDetailsService`.

## Security constraints

- Passwords stored as BCrypt hashes (Spring Security `BCryptPasswordEncoder`)
- Refresh token IDs are UUID v4 (never the username or deterministic value)
- Expired or unknown refresh tokens return 401, not 403
- `ROLE_ADMIN` endpoints return 403 (not 401) when a valid but unprivileged token is used
- MiFID II disclaimer is not required on auth endpoints (no valuation data)
