# J3 Google Sign-In Security, Integration & Operational Validation Requirements

## Scope

- Validate Google identity resolution and linking behavior beyond the J2 happy path: new verified user, existing email match, repeat login, unverified email, conflicting provider subject, and role preservation.
- Add handler-level validation for Google callback outcomes using mocked OIDC users so platform JWT issuance, refresh-cookie setting, handoff-code creation, and rejection paths are covered without calling Google.
- Preserve the existing security boundary: `/api/**` remains JWT-protected, password login/refresh/logout behavior is unchanged, and Google sign-in never grants or mutates elevated roles.
- Add structured security events and metrics for Google sign-in success, rejection, account creation/linking, repeat identity reuse, and token handoff exchange outcomes.
- Document Google Cloud Console setup, redirect URI requirements, local development configuration, secret rotation, and compromised-client-secret incident response.

## Exclusions

- No live Google OAuth flow or browser automation against real Google accounts.
- No change to the authorization model, user roles, local password login, or refresh-token duration.
- No migration beyond the existing OAuth identity persistence unless implementation discovers a missing required index.
- No storage or logging of Google ID tokens, authorization codes, client secrets, or sensitive profile claims.

## Decisions

- Use existing Spring Boot and JUnit infrastructure; add focused tests around `OAuthLoginSuccessHandler`, `OAuthTokenController`, and `OAuthAccountResolver`.
- Treat mocked `OidcUser` instances as the integration boundary for callback success/failure because real Google validation is Spring Security's responsibility once client configuration is correct.
- Emit security events through a small backend service using structured log key/value pairs and Micrometer counters when a `MeterRegistry` is present.
- Keep frontend validation lightweight: rely on existing TypeScript build for login/callback route regressions rather than adding a browser framework in this phase.

## Assumptions

- J2 has already merged the React login button, OAuth callback route, account settings linked-state display, and unlink safeguards.
- The existing OAuth backend already configures Spring Security's OIDC provider validation; J3 adds local behavior coverage and operational visibility, not a custom JWT/JWK implementation.
- The repository's current validation commands are backend Maven tests and frontend TypeScript/Vite build.
- The earliest valid unstarted roadmap phase is J3 because J2 is already merged on `main` and no `2026-06-30-j3-*` spec exists.

## Dependencies

- Spring Boot 3.x, Spring Security OAuth2 Client, Micrometer, Redis handoff store, and existing RS256 JWT service.
- Existing domain repositories for `User` and `OAuthIdentity`.
- Existing frontend Vite/React authentication pages from J2.
