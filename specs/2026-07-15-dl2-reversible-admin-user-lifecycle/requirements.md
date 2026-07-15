# Requirements — DL2: Reversible ADMIN User Lifecycle

## Purpose

Make ADMIN user provisioning reversible and testable without deleting user-owned portfolios, watchlists, preferences, or immutable research evidence. The real-demo walkthrough could inspect the create-only ADMIN surface but could not create a QA account safely because no product-level cleanup or disable operation existed.

DL2 completes account administration through reversible activation state. It follows the role and ownership boundaries in `specs/mission.md` and the Spring Security/JWT, PostgreSQL, React, and TanStack Query choices in `specs/tech-stack.md`.

## Scope

- Replace the create-only controller logic with an ADMIN user lifecycle service.
- Add an ADMIN-only paginated user list containing ID, normalized email, role, active state, and creation timestamp.
- Include `active` in create responses and lifecycle responses without exposing password hashes, OAuth identifiers, tokens, cookies, or other secrets.
- Add an idempotent enable/disable operation based on the existing `app_user.active` field.
- Prevent self-disable and prevent disabling the final active ADMIN.
- Ensure disabled users cannot complete password login, OAuth login/account resolution where applicable, or refresh an access token.
- Keep already-issued access tokens valid only until their normal short expiry; document the maximum 15-minute residual session window.
- Upgrade `/admin/users` to show a paginated lifecycle table plus the existing create form.
- Add explicit enable/disable confirmations, pending states, conflict messaging, and list refresh.
- Preserve all user-owned and immutable historical records during disable/enable cycles.

## Existing Context

- `User.active` already exists, defaults to `true`, and is mapped to `app_user.active`.
- `UserDetailsServiceImpl` already passes `active` into Spring Security, so disabled users cannot complete password authentication.
- `AdminUserController` currently supports only `POST /api/v1/admin/users` and directly uses `UserRepository` and `PasswordEncoder`.
- `AuthController.refresh` currently resolves the user's role but does not reject inactive users.
- `JwtAuthenticationFilter` validates signed access-token claims without a database lookup.
- Access tokens expire after 900 seconds; refresh tokens expire after seven days and are stored in Redis.
- The frontend `/admin/users` page is ADMIN-gated but contains only a creation form.
- Existing security configuration already protects `/api/v1/admin/**` with the ADMIN role.

## API Contract

### List users

`GET /api/v1/admin/users?page=0&size=20&sort=createdAt,desc`

Returns a standard Spring page with items shaped as:

```json
{
  "id": "6a1b30db-5752-47d6-a173-83831a310b18",
  "email": "investor@example.com",
  "role": "INVESTOR",
  "active": true,
  "createdAt": "2026-07-15T12:00:00"
}
```

Default sort is `createdAt DESC`. Maximum page size is bounded to prevent unbounded ADMIN responses.

### Create user

`POST /api/v1/admin/users` retains the existing request shape and `201 Created` behavior. The response uses the lifecycle representation above and includes `active=true`.

### Change active state

`PATCH /api/v1/admin/users/{id}/active`

```json
{
  "active": false
}
```

Returns `200 OK` with the updated lifecycle representation. Repeating the same desired state is successful and does not create a conflicting transition.

## Decisions

### Disable instead of delete

No user-delete endpoint is introduced. Deactivation preserves portfolios, watchlists, account links, preferences, audit evidence, and ownership relationships. Re-enabling restores access without data reconstruction.

### Access-token residual window is accepted and explicit

DL2 does not add a database lookup to every authenticated API request. An access token issued before deactivation can remain valid until its existing 15-minute expiry. New password login and refresh attempts are denied immediately. The ADMIN confirmation and success message must state this bounded residual window.

### Refresh tokens are retained but unusable while inactive

Refresh validation must resolve the current user and require `active=true`. The Redis token may remain until its normal expiry; re-enabling the account can make a still-valid refresh token usable again. Global token-family revocation and per-user session inventory are deferred.

### Self-disable and final-ADMIN disable return conflict

Both operations return `409 Conflict` with stable, actionable error codes. The backend, not the UI, enforces these invariants. Enabling an account is always allowed for an authorized ADMIN.

### Final-ADMIN protection must be concurrency-safe

The disable transition must run transactionally and lock or otherwise serialize the active-ADMIN invariant so concurrent requests cannot disable the last active administrators simultaneously.

### ADMIN cannot bypass its own lifecycle rules

The authenticated ADMIN may manage other users but cannot disable their own current account through this endpoint. Cross-account role changes and password resets are outside DL2.

## Error Semantics

- `400 Bad Request`: invalid pagination, malformed UUID/body, null active state, invalid create request or role.
- `401 Unauthorized`: missing/invalid authentication.
- `403 Forbidden`: authenticated non-ADMIN caller.
- `404 Not Found`: target user does not exist.
- `409 Conflict`: duplicate email, self-disable, or final-active-ADMIN protection.

Conflict payloads must use stable codes such as `EMAIL_ALREADY_REGISTERED`, `SELF_DISABLE_NOT_ALLOWED`, and `LAST_ACTIVE_ADMIN` plus a human-readable message.

## Frontend Requirements

- Keep the existing creation form and add a lifecycle table on the same ADMIN page.
- Display email, role, active status, creation time, and enable/disable action.
- Use TanStack Query for list/pagination and mutation invalidation.
- Disable the current user's own disable action and explain why.
- Require a confirmation naming the user and explaining that owned data is preserved.
- For disable, explain that existing access may remain usable for up to 15 minutes.
- Distinguish conflict, not-found, authorization, and generic server failures.
- Provide loading, empty, retry, pending, and success states without exposing raw server internals.

## Guardrails

- Never serialize `passwordHash`, refresh/access tokens, OAuth subject identifiers, cookies, secrets, or raw authentication objects.
- Never physically delete user-owned or immutable records.
- Do not reveal user lists or lifecycle controls to INVESTOR or ADVISOR navigation.
- Normalize and compare emails consistently with the existing authentication flow.
- Do not silently change roles during enable/disable.
- Do not report immediate logout when an existing access token may remain valid.
- Preserve decision-support data and audit history exactly as stored.

## Out of Scope

- Physical user deletion or GDPR erasure workflow.
- Role changes.
- Password reset or temporary-password rotation.
- Per-user session list or remote logout.
- Immediate access-token revocation/denylist.
- Refresh-token family revocation on disable.
- Bulk enable/disable.
- Search beyond normal page/filter evolution unless needed for page usability.
- ADMIN impersonation.

## Resolved Feature-Spec Questions

- Lifecycle model: reversible `active` flag, never physical delete.
- Endpoint: idempotent `PATCH /{id}/active`.
- Listing: bounded Spring pagination, newest first.
- Self-disable: prohibited.
- Last active ADMIN: prohibited with concurrency-safe enforcement.
- Existing access tokens: valid until normal expiry, maximum 15 minutes.
- Login and refresh after disable: rejected immediately.
- Owned data and audit evidence: fully retained.

