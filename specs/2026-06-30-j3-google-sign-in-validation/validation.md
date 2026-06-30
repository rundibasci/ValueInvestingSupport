# J3 Google Sign-In Security, Integration & Operational Validation

## Acceptance Checks

- New and existing Google identities resolve to the correct platform user without duplicate users or role escalation.
- Conflicting provider-subject and email cases are rejected or preserve the original provider identity.
- Callback success issues a platform access token, sets the platform refresh cookie, stores a single-use handoff code, and redirects only to the configured frontend callback.
- Callback rejection for unverified email does not create or link accounts and emits a sanitized security event.
- Handoff code exchange returns a token once, rejects repeated/unknown codes, and leaves `/api/**` protected without a JWT.
- Security events and counters do not contain ID tokens, authorization codes, Google client secrets, refresh tokens, or sensitive profile claims.
- Operational docs identify exact redirect URI configuration, local setup, secret rotation, and compromised-secret response.

## Validation Commands

- `cd backend; .\mvnw.cmd -Dtest=OAuthAccountResolverTest,OAuthAccountLinkingIntegrationTest,OAuthTokenExchangeTest,OAuthLoginSuccessHandlerTest test`
- `cd backend; .\mvnw.cmd test`
- `cd frontend; npm run build`

## Manual QA

- Review OAuth logs/telemetry code for sensitive value leakage.
- Review runbook commands and redirect URI examples for environment-specific placeholders rather than real secrets.

## Merge Readiness

- All validation commands pass.
- Worktree contains only the J3 spec, OAuth validation/telemetry code, docs, tests, vault note, and changelog updates.
- No secret values are introduced.

## Known Risks

- Real Google provider behavior is not exercised in CI; this phase relies on Spring Security provider validation plus mocked OIDC callback tests.
- Redis outage fallback remains in-memory and process-local, suitable for local resilience but not cross-instance production handoff.
