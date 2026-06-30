# J2 Google Sign-In UI & Account Lifecycle Validation

## Acceptance Checks

- Login page displays a Google sign-in action that starts `/oauth2/authorization/google`.
- Callback route exchanges a valid handoff code through `/auth/oauth2/token`, initializes the same session model as password login, and redirects away from the callback URL.
- Callback route shows clear failure states for provider denial, cancellation, missing code, and expired or invalid handoff codes.
- Account settings shows email, role, Google-linked status, and whether a local password credential exists.
- Google unlink succeeds only for accounts with a local password credential.
- Google unlink is blocked when Google is the only sign-in method.
- Password login, refresh restoration, logout, and JWT-protected routes remain unchanged.

## Test Strategy

- Backend focused tests for account status and unlink behavior.
- Frontend TypeScript production build to catch route, type, and component regressions.
- Full backend Maven test suite before merge.

## Commands

- `cd backend && .\mvnw.cmd test -Dtest=AccountControllerTest`
- `cd frontend && npm run build`
- `cd backend && .\mvnw.cmd test`

## Manual QA

- With a configured Google OAuth client, click **Continue with Google**, complete the provider flow, and confirm the user lands on the originally requested protected route.
- Visit account settings and verify linked-state and unlink copy.
- Confirm logout only ends the platform session and does not attempt a global Google logout.

## Merge Readiness

- Spec files exist and are non-empty.
- Focused backend tests pass.
- Frontend build passes.
- Full backend test suite passes.
- Changelog and Obsidian activity log document the completed phase and validation commands.

## Known Risks

- Live Google provider behavior cannot be fully validated without environment-specific client credentials and redirect URI configuration.
- J3 remains responsible for mocked-provider callback validation, browser automation, structured security events, metrics, and operational setup documentation.
