# J2 Google Sign-In UI & Account Lifecycle Requirements

## Scope

- Add a React **Continue with Google** action to the existing login page that starts the backend OAuth2 authorization entry point.
- Complete the frontend callback route that exchanges the backend one-time handoff code for the platform access token, initializes the normal session, clears the callback URL, and returns the user to the intended route or dashboard.
- Surface account identity status for the authenticated user, including email, role, and whether a Google identity is linked.
- Provide an authenticated Google unlink action only when the user still has a usable local password credential.
- Preserve the existing username/password login, refresh-cookie session restoration, logout semantics, JWT authorization model, and demo decision-support boundaries.

## Exclusions

- J3 mocked OIDC provider integration tests, browser tests, and operational runbooks remain a later phase.
- No global Google sign-out is attempted on platform logout.
- No Google access tokens or ID tokens are stored or displayed in the browser.
- No new role elevation, role assignment, or admin identity-management behavior is introduced.

## Decisions

- The frontend uses the existing `VITE_API_BASE_URL`-derived API base for `/oauth2/authorization/google` and `/auth/oauth2/token`.
- The requested protected route is stored in `sessionStorage` only long enough to complete the OAuth handoff.
- Account lifecycle data is exposed by a new authenticated backend endpoint under `/api/v1/account`.
- Google unlink is a backend operation under `/api/v1/account/oauth/google`; it rejects unlinking when the user has no local password hash.
- The UI adds a compact account settings page in the authenticated app shell rather than embedding account lifecycle controls in unrelated workflow pages.

## Assumptions

- J1 backend OAuth success already writes the httpOnly refresh cookie and returns a short-lived handoff code.
- `User.passwordHash == null` means the account cannot use local password authentication and therefore must not be allowed to unlink its only Google sign-in method.
- OAuth provider errors may return to the callback route as query parameters such as `error`, `error_description`, or without a `code`; those states are handled as failed provider flows.
- A successful callback can rely on the access-token JWT payload for the active session email and role, matching the existing password-login session model.

## Dependencies

- Existing J1 classes: `OAuthLoginSuccessHandler`, `OAuthTokenController`, `OAuthAccountResolver`, `OAuthIdentity`, and OAuth security configuration.
- Existing frontend auth context, token storage helper, and authenticated route shell.
- Existing repository methods for `User` and OAuth identity persistence.

## Context

The roadmap places J2 after VM, SR, and MA because the analytical engine is now trustworthy enough to resume identity UX work. This phase completes the browser-facing portion of Google sign-in while keeping the platform as the authorization authority and preserving the local JWT/session model.
