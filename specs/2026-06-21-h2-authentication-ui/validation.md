# H2 — Authentication UI Validation

H2 is complete and mergeable only when all checks below pass.

## Automated checks

- Frontend TypeScript strict-mode check and production build succeed.
- Frontend tests cover: successful login; invalid credentials; protected-route redirect to login and return to the original location; boot-time refresh; one retry after access-token expiry; failed refresh/session-expired flow; logout; and ADMIN-only provisioning visibility.
- Backend tests verify: login issues the httpOnly refresh cookie; refresh accepts the cookie and returns a fresh access token; invalid/expired/revoked refresh cookies are rejected; logout revokes server state and clears the cookie; existing `/api/v1/admin/users` authorization remains enforced.
- No test, snapshot, build artifact, log, or browser-storage assertion reveals access or refresh token values.

## Browser acceptance checks

| Scenario | Expected result |
| --- | --- |
| Visit `/screener` while signed out | Redirected to `/login`; after successful login returns to `/screener`. |
| Login with valid credentials | App shell loads; header shows authenticated identity/role; protected API calls carry the in-memory bearer token. |
| Login with invalid credentials | Form retains safe input state, presents a clear non-sensitive error, and remains usable. |
| Reload with a valid refresh cookie | Session restores without showing protected content before auth resolution completes. |
| Expired access token with valid refresh cookie | One transparent refresh/retry succeeds; no duplicate requests or refresh loop. |
| Invalid/expired refresh cookie | Session clears and `/login` explains that the session expired; no protected content remains visible. |
| Logout | Backend refresh token is revoked, refresh cookie is cleared, client token/identity are cleared, and protected routes redirect to login. |
| ADMIN provisioning | ADMIN can create a user and sees success/conflict validation; INVESTOR and ADVISOR do not see the action and receive no elevated access. |
| Small and keyboard-only viewport | Login and provisioning controls remain readable, labelled, focusable, and usable; focus indicators and error messages are perceptible. |

## Security and merge gates

- Access tokens are memory-only; refresh tokens are httpOnly cookies. Neither appears in localStorage, sessionStorage, URLs, committed configuration, or client logs.
- Cookie, CORS, and credential behavior works for documented local and deployed origins without broad wildcard credential settings.
- Existing backend JWT and role protection stays authoritative; hiding UI never substitutes for server authorization.
- The final UI follows H1's design system while delivering the agreed dark-slate/emerald research aesthetic and contains no investment recommendation or advice language.
- Working tree contains only intended H2 changes plus pre-existing user-owned files; the branch is `phase/h2-authentication-ui`.
