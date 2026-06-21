# H2 — Authentication UI Plan

## 1. Confirm and harden the auth transport contract

1.1 Inspect and preserve the existing login, refresh, logout, and admin-user response contracts.

1.2 Change refresh-token delivery to an httpOnly cookie, with environment-appropriate `Secure`, `SameSite`, path, expiry, CORS, and credential settings. Ensure logout clears the cookie and revokes the server-side token.

1.3 Add/update backend tests for login cookie issuance, refresh from cookie, logout clearing/revocation, invalid/expired refresh behavior, and the existing role boundary for user provisioning.

## 2. Build the frontend authentication foundation

2.1 Define typed auth request/response/domain models and centralize auth-aware requests in the API layer.

2.2 Implement `AuthProvider` and a focused `useAuth` hook: boot-time restoration, access-token memory ownership, decoded identity/role, login, refresh, logout, and deterministic clearing.

2.3 Add one guarded refresh/retry response path for protected requests. Prevent duplicate concurrent refreshes and refresh loops.

## 3. Protect routes and integrate the shell

3.1 Add public `/login` and an authenticated-route wrapper around the existing application routes.

3.2 Preserve requested location and restore it after successful login; safely redirect unknown/invalid destinations to `/`.

3.3 Add loading/session-expired states and integrate authenticated identity, role, and logout controls into `AppShell`.

## 4. Deliver the login and admin-provisioning experiences

4.1 Build a responsive, keyboard-accessible login page: prominent brand mark, research-thesis visual card, email/password controls, inline validation, password visibility control, loading state, and recoverable errors.

4.2 Build the ADMIN-only user-provisioning surface using `/api/v1/admin/users`. Include email, password, and role validation; success confirmation; duplicate-email and authorization error handling.

4.3 Ensure non-admin users cannot discover the provisioning action through normal navigation, while treating backend authorization as the definitive protection.

## 5. Verify, document, and prepare merge evidence

5.1 Add focused frontend tests for auth state transitions, guarded routes, redirect return, refresh success/failure, and role-gated provisioning UI.

5.2 Run TypeScript checks, production build, relevant backend tests, and browser-level manual/automated checks against the API.

5.3 Verify responsive layout, keyboard navigation, visible focus, error announcement, contrast, and that no token reaches browser storage, URL, or console.
