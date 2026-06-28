# Requirements — J1: Google OpenID Connect Backend

## Scope

Add Google OpenID Connect sign-in to the Spring Boot backend. Google is used only to prove identity; the platform remains the authorization authority. After this phase, a Google-authenticated user receives the same RS256 access + refresh tokens issued by `POST /auth/login`. No frontend/React changes; no new roles; no changes to existing username/password login.

## What's in scope

- Spring Security OAuth2 Client support for Google Authorization Code + OpenID Connect
- `GET /oauth2/authorization/google` as the login entry point
- Callback at `/login/oauth2/code/google` — public path; `/api/**` remains JWT-protected
- Google client configuration exclusively through environment variables (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, redirect URI)
- `.env.example` updated with placeholders and setup guidance
- ID token validation: issuer, audience, signature, expiry, nonce/state, `email_verified=true`
- `OAuthIdentity` entity linking `provider=GOOGLE` + `providerSubject` (Google `sub` claim) to an existing `User` record
- Flyway migration adding `oauth_identity` table with uniqueness on `(provider, provider_subject)` and normalized unique user email
- Auto-provisioning: on first Google sign-in, create an `INVESTOR` user if the verified email is not already registered
- Account linking: if a password user with the same verified email exists, link the Google identity to that user without creating a duplicate
- Google sign-in must never grant or change `ADMIN` or `ADVISOR` roles
- After successful Google auth, issue the same RS256 access + refresh tokens used by `POST /auth/login`
- Redirect to the configured frontend callback without exposing tokens in query parameters; use the existing httpOnly refresh-cookie pattern and a short-lived, single-use handoff mechanism for the access token
- Preserve existing username/password login, refresh, logout, JWT revocation, and demo-profile behavior

## What's out of scope

- React UI changes (deferred to J2)
- Google sign-in button or frontend callback route (J2)
- Account unlinking (J2)
- Browser tests (J3)
- Structured security events/metrics for Google sign-in (J3)
- Google Cloud Console setup documentation (J3)

## Decisions

| Decision | Choice | Reason |
|---|---|---|
| OAuth2 library | Spring Security OAuth2 Client | Native Spring Boot integration; handles authorization code flow, token exchange, JWK validation |
| Scopes | `openid`, `email`, `profile` | Minimal data; email for matching, profile for display name |
| Identity key | Google `sub` claim (immutable) | Display name and email can change; `sub` is stable |
| Auto-provision role | `INVESTOR` | Lowest privilege; admin/advisor promotion is manual |
| Email matching | Verified email (`email_verified=true`) only | Prevents account hijacking via unverified Google email |
| Token handoff | Short-lived single-use code/token stored server-side; frontend exchanges it for the access token | Avoids tokens in URL fragments or query params |
| Config source | Env vars only (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`) | Consistent with existing secrets pattern; never in committed files |
| Callback path | `/login/oauth2/code/google` | Spring Security OAuth2 Client default; public alongside `/auth/**` |

## Key env vars (additions for J1)

```
GOOGLE_CLIENT_ID        Google OAuth2 client ID
GOOGLE_CLIENT_SECRET    Google OAuth2 client secret
GOOGLE_REDIRECT_URI     Allowed redirect URI (e.g., http://localhost:8080/login/oauth2/code/google)
```

## Dependencies

- A3 authentication (JWT infrastructure, `JwtService`, `SecurityConfig`, `User` entity, Redis refresh tokens) — already merged
- H2 demo profile compatibility — OAuth2 config must be optional/conditional so the demo profile still works without Google credentials

## Security constraints

- Google `sub` claim is the identity anchor; never use mutable display name as identifier
- `email_verified` must be `true` before any account creation or linking
- ID token must be validated for issuer (`accounts.google.com`), audience (client ID), signature (Google JWK set), and expiry
- State/nonce parameters must be validated to prevent CSRF/replay
- No Google access tokens, authorization codes, or client secrets may appear in logs or committed files
- Google sign-in never grants `ADMIN` or `ADVISOR` roles, even if the email matches an existing admin/advisor user (role is preserved, not elevated)
- The handoff mechanism for the access token must be single-use and short-lived (< 60 seconds)
