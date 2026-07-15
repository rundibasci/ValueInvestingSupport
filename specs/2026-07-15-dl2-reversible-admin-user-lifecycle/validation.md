# Validation — DL2: Reversible ADMIN User Lifecycle

## Functional Acceptance

- ADMIN can list users through a bounded paginated endpoint.
- Every list/create/update item contains only ID, email, role, active state, and creation time.
- Password hashes, OAuth subjects, access/refresh tokens, cookies, and secrets never appear.
- ADMIN can create a user and the user appears as active in the refreshed list.
- ADMIN can disable and re-enable another user without deleting owned or historical data.
- Repeating the requested active state returns `200` and the same effective state.
- Self-disable returns `409 SELF_DISABLE_NOT_ALLOWED`.
- Disabling the final active ADMIN returns `409 LAST_ACTIVE_ADMIN`.
- Concurrent disable attempts cannot leave the system with zero active ADMIN users.
- Unknown target ID returns `404`.
- INVESTOR and ADVISOR cannot list or mutate users; unauthenticated requests receive `401`.
- Disabled users cannot perform a new password login or refresh an access token.
- A previously issued access token may remain valid only until its normal expiry, documented as at most 15 minutes.
- Re-enabling restores login and permits a still-valid refresh token according to the documented policy.
- The frontend names the target user in confirmations and accurately explains preservation and residual-session behavior.

## Backend Automated Checks

- Lifecycle response serialization includes `active` and excludes every credential/token field.
- Pagination defaults, bounds, ordering, empty page, and subsequent pages are correct.
- Create retains validation and duplicate-email conflict behavior.
- Active-state request rejects missing/null values.
- Service enable/disable operations are idempotent.
- Service rejects self-disable before mutation.
- Service rejects final-active-ADMIN disable under a transaction-safe lock.
- Integration concurrency test proves at least one ADMIN remains active.
- Missing user is `404`; lifecycle conflicts are stable `409` payloads.
- Security tests prove ADMIN-only access.
- Disabled password login is rejected without account-enumerating detail.
- Disabled refresh is rejected and issues no access token.
- OAuth credential issuance for an inactive existing account is rejected.
- Persistence tests verify portfolios, holdings, watchlists, preferences, OAuth identities, and research snapshots survive disable/re-enable.

Suggested commands, adjusted to implementation test names:

```bash
cd backend
./mvnw -Dtest=AdminUserControllerTest,AdminUserLifecycleServiceTest,AuthControllerTest,UserDetailsServiceImplTest test
./mvnw -Pintegration-test -Dtest=AdminUserLifecycleIT test
./mvnw test
./mvnw -DskipTests package
```

Use Java 21 and PostgreSQL 16 for integration evidence.

## Frontend Automated Checks

- API client tests verify list pagination, create, PATCH body, success mapping, and structured failures.
- Page tests verify ADMIN guard and absence from non-ADMIN navigation.
- Creation refreshes the current list and reports duplicate-email conflict correctly.
- Enable/disable confirmation includes the target email.
- Disable confirmation states data preservation and the 15-minute maximum access-token window.
- Self-disable control is unavailable with an explanation.
- Pending state is row-scoped and blocks duplicate mutation.
- Conflict, not-found, retryable error, empty, loading, and pagination states render correctly.
- Successful lifecycle mutation updates the displayed status without full-page reload.

Suggested commands:

```bash
cd frontend
npm run typecheck
npm run build
```

Run the repository's frontend test command if a test runner is introduced or already available at implementation time.

## Manual Review

1. Sign in as an ADMIN while at least one other ADMIN is active.
2. Open `/admin/users` and verify pagination and absence of secret fields in the network response.
3. Create a temporary INVESTOR and verify the row appears active.
4. Sign in as that INVESTOR and create representative owned records if the environment permits.
5. Return as ADMIN, cancel disable confirmation, and verify no request is sent.
6. Confirm disable; verify the message names the user, states data preservation, and mentions the maximum 15-minute residual access window.
7. Verify new password login and refresh fail for the disabled user.
8. Verify the ADMIN cannot disable their own account.
9. With only one active ADMIN remaining, verify disabling it is rejected.
10. Re-enable the temporary user and verify login succeeds and owned records remain.
11. Restore intended final account states through the UI; no direct PostgreSQL cleanup is required.

## Regression and Safety Checks

- Existing ADMIN user creation remains compatible except for the additive `active` response field.
- Login, refresh, logout, OAuth login/linking, account page, and role authorization continue to work for active users.
- Portfolio, watchlist, preferences, checklist, audit, and research records are never deleted during lifecycle changes.
- Access-token validation remains stateless and does not add a database query to every API request.
- Error responses do not reveal password/token data or raw persistence errors.
- Existing `realDemo`, local, and non-demo profile behavior remains intentional.
- `git diff --check` passes.

## Merge Criteria

- All functional acceptance statements pass.
- Concurrency-safe final-ADMIN protection is demonstrated on PostgreSQL.
- Targeted backend/auth/security tests and integration tests pass on Java 21.
- Frontend typecheck and production build pass.
- Backend package passes; full-suite results contain no new DL2 regression.
- ADMIN can complete the QA create-disable-enable cycle through the product.
- Documentation states the exact residual-session behavior and never claims immediate access-token revocation.
- No unrelated feature or data-model behavior is changed.

