# Plan — DL2: Reversible ADMIN User Lifecycle

## Task Group 1: Lifecycle DTOs and Repository Queries

1.1 Extend or replace `UserResponse` with a lifecycle-safe response containing `id`, `email`, `role`, `active`, and `createdAt` only.

1.2 Add a validated active-state request record with non-null `active`.

1.3 Add stable error codes/payloads for duplicate email, self-disable, and final-active-ADMIN conflicts, following existing global error conventions.

1.4 Add repository queries for bounded pagination and concurrency-safe active-ADMIN protection. Prefer a pessimistic lock or equivalent transaction-safe strategy over an unlocked count-then-update sequence.

1.5 Verify indexes supporting default list ordering and active ADMIN lookup; add a Flyway migration only if query plans or missing constraints justify it.

## Task Group 2: ADMIN User Lifecycle Service

2.1 Extract create logic from `AdminUserController` into `AdminUserLifecycleService` with normalized email, duplicate detection, password encoding, validated role conversion, and safe response mapping.

2.2 Implement paginated listing with a bounded maximum page size and deterministic `createdAt DESC`, then ID, ordering.

2.3 Implement transactional, idempotent `setActive(Authentication, UUID, boolean)`.

2.4 Resolve the authenticated ADMIN by email and reject `active=false` when the target is the current user.

2.5 When disabling an ADMIN, lock the relevant active-admin set or invariant, verify another active ADMIN remains, then persist the transition.

2.6 Preserve all relationships and avoid delete/cascade operations.

## Task Group 3: ADMIN API

3.1 Add `GET /api/v1/admin/users` with page/size support and lifecycle-safe responses.

3.2 Retain `POST /api/v1/admin/users`, delegate to the service, and return the enriched response including `active=true`.

3.3 Add `PATCH /api/v1/admin/users/{id}/active` returning the updated representation.

3.4 Map lifecycle conflicts to `409`, missing users to `404`, validation errors to `400`, and rely on existing security for `401/403`.

3.5 Confirm every endpoint remains excluded from the `demo` profile consistently with the existing controller.

## Task Group 4: Authentication Enforcement

4.1 Update refresh handling to load the current user and reject inactive accounts before issuing a new access token.

4.2 Verify password login already rejects inactive users through `UserDetailsServiceImpl`; add regression tests instead of duplicating the check.

4.3 Verify OAuth account resolution/success paths cannot issue new credentials to an inactive existing account; add the smallest explicit guard if the current flow bypasses `UserDetailsServiceImpl`.

4.4 Preserve stateless access-token validation and the documented maximum 15-minute residual window; do not add a per-request database lookup.

4.5 Ensure authentication failure messages do not disclose whether an account is disabled versus credentials being invalid where disclosure would aid account enumeration.

## Task Group 5: Backend Tests

5.1 Controller tests: list pagination, create, enable, disable, validation, not-found, conflict payloads, and response secret redaction.

5.2 Service tests: idempotent transitions, self-disable rejection, last active ADMIN rejection, multi-ADMIN success, enabling an inactive user, and preservation of role/data ownership.

5.3 Concurrency/integration test: simultaneous attempts cannot leave zero active ADMIN users.

5.4 Security tests: unauthenticated `401`, INVESTOR/ADVISOR `403`, ADMIN success.

5.5 Authentication tests: inactive password login rejected, inactive refresh rejected, re-enabled user can login/refresh when credentials/token remain valid, existing access-token behavior remains bounded by expiry.

5.6 Persistence integration test: disabling and re-enabling a user preserves portfolio, holdings, watchlist, preferences, OAuth identity, and research snapshot rows.

## Task Group 6: Frontend API and Page Structure

6.1 Create a typed ADMIN users API module for list, create, and active-state mutation with structured error handling.

6.2 Refactor `UserProvisioningPage` into readable React/TypeScript structure while retaining the creation form.

6.3 Add a TanStack Query paginated lifecycle table showing email, role, active status, and creation date.

6.4 Add enable/disable mutations and invalidate the current page after success.

6.5 Disable self-disable in the UI using the authenticated session email, but keep backend enforcement authoritative.

## Task Group 7: Frontend Lifecycle UX

7.1 Require confirmation naming the target account before enable/disable.

7.2 For disable, state that data is preserved and existing access may remain active for up to 15 minutes.

7.3 Show pending state only on the affected row and prevent duplicate submission.

7.4 Render stable conflict messages for self-disable and last-active-ADMIN rules; render retryable states for transient failures.

7.5 Add loading, empty, pagination, success, and error states with keyboard-accessible controls and mobile-readable layout.

7.6 Refresh the list after create so the new active user appears without reloading the page.

## Task Group 8: Documentation and Validation

8.1 Document the list/create/active-state contracts, role restrictions, conflict codes, and 15-minute residual access-token window.

8.2 Update the real-demo walkthrough follow-up so an ADMIN QA user can be created, disabled, and optionally re-enabled without database cleanup.

8.3 Run focused backend tests, auth/security tests, PostgreSQL integration tests, frontend typecheck/build, backend package/full suite, and `git diff --check`.

8.4 Perform a real-demo smoke test with two ADMIN accounts and one INVESTOR account; restore intended final active states through the product.

