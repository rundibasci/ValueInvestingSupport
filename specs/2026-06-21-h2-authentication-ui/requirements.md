# H2 — Authentication UI

## Context

H1 established the React 18, TypeScript, Vite, Tailwind, React Router, TanStack Query, API-client, and in-memory access-token foundation. The next roadmap phase is H2: Authentication UI. It is the browser entry point to a financial decision-support product: it must be clear, calm, and trustworthy while preserving the platform's existing backend security boundary.

The backend already provides `POST /auth/login`, `POST /auth/refresh`, and `POST /auth/logout`. Access tokens are short lived (15 minutes) and are attached by `apiFetch`; refresh tokens are currently returned in JSON. The roadmap calls for an access token held only in memory and an httpOnly refresh cookie. The existing backend will need the small contract change necessary to put the refresh token in a Secure, httpOnly cookie; the browser must never persist either token in localStorage or sessionStorage.

User creation is an existing admin-only capability at `POST /api/v1/admin/users`; this is provisioning, not public self-registration. H2 will expose it only to an authenticated ADMIN, and must not offer public account creation.

## Scope

- A polished `/login` page with email and password validation, loading, invalid-credential, network-failure, and accessible error states.
- A deliberately distinctive “research before conviction” visual treatment: dark slate foundation, emerald value signal, measured editorial typography, and a compact thesis card. It must remain responsive and accessible rather than becoming decoration-first.
- `AuthProvider` / hook owns the in-memory access token, decoded identity/role, session state, login, refresh, and logout operations.
- Silent session restoration on application start and a single refresh/retry path for an expired access token. A failed refresh clears client state and returns the user to `/login` with a human-readable expiration message.
- Route guard for all existing application routes; capture the requested destination and restore it after successful authentication. `/login` remains public and redirects an already authenticated visitor to the intended route or `/`.
- App-shell account affordance: authenticated email/role, logout action, and no authenticated navigation before session restoration has resolved.
- An ADMIN-only user-provisioning form/action using the existing admin endpoint, with clear role choices, validation, success feedback, and conflict/error handling. It must be absent from non-admin UI and must never weaken server authorization.
- Frontend/backend cookie configuration required to make refresh cookies work in local development and deployed environments (credentials included on refresh/logout requests, CORS/SameSite/Secure settings documented and tested).

## Decisions

| Topic | Decision |
| --- | --- |
| Access token | Store only in module/context memory; attach as `Authorization: Bearer` through the existing API client. |
| Refresh token | Backend sets an httpOnly, Secure cookie with an appropriate SameSite policy; JS cannot read it. Refresh and logout use `credentials: 'include'`. |
| Expiry | Proactively restore on app load; on one 401 retry once after refresh. If that fails, clear state and show a session-expired message on login. Never loop. |
| Registration | No public sign-up. “Create user” is an ADMIN-only provisioning operation backed by `/api/v1/admin/users`. |
| Authorization | React guards improve navigation, but backend `/api/**` JWT and role checks remain authoritative. |
| Redirect | Preserve the original in-app path, query, and hash; after login send the user there, otherwise `/`. |
| UX | Build on H1's slate/emerald design, with an intentional investment-research tone and WCAG-conscious labels, focus states, contrast, and keyboard behavior. |
| Future compatibility | Keep the login surface extensible for Group J Google sign-in; do not implement Google OAuth or expose provider tokens in H2. |

## Out of Scope

- Google sign-in, callback handling, account linking/unlinking (Group J).
- Password reset, email verification, MFA, and public self-service registration.
- Changes to JWT claims, token lifetimes, or the platform's roles.
- The screener, dashboard, security-detail, portfolio, or watchlist feature interfaces.

## Constraints from Mission and Stack

- This is decision-support software, not investment advice; authentication UI must not make investment claims or obscure the product's MiFID II boundary.
- Use React 18, TypeScript strict mode, TailwindCSS, React Router v6, and the H1 API client. Keep server state in TanStack Query only where it helps; session identity belongs in the auth provider.
- Secrets and token values must never enter committed source, browser storage, URLs, logs, telemetry, or error messages.
