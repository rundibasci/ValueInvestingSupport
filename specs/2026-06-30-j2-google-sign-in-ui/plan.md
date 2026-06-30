# J2 Google Sign-In UI & Account Lifecycle Plan

1. Backend account lifecycle API
   - Add an authenticated account response DTO with email, role, Google-linked status, and local-password availability.
   - Add a service that resolves the current user, checks Google identity links, and unlinks Google only when a local password credential exists.
   - Add `GET /api/v1/account` and `DELETE /api/v1/account/oauth/google`.
   - Add focused unit or MVC tests for status, successful unlink, blocked sole-sign-in unlink, and unauthenticated access.

2. Frontend OAuth session flow
   - Extend the auth provider with a reusable access-token adoption method for OAuth handoff responses.
   - Add a callback route that handles success, provider cancellation/denial, expired handoff codes, and redirects after completion.
   - Store and consume a short-lived return path around Google login initiation.

3. Login and account UI
   - Add a **Continue with Google** action to the login page with loading and provider-unavailable states.
   - Preserve existing password login behavior and local error messaging.
   - Add an authenticated account settings page showing email, role, Google-linked state, local password availability, and unlink controls.
   - Wire the account page into the existing app shell navigation.

4. Validation and documentation
   - Run focused backend account/OAuth tests.
   - Run the frontend build.
   - Run the backend Maven test suite if focused validation passes.
   - Update changelog and record validation evidence for merge.
