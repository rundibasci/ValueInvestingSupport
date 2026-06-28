# Validation — J1: Google OpenID Connect Backend

## Definition of done

All items below must pass before this phase can be merged into `main`.

---

## 1. Unit tests — Account resolution

All tests in `OAuthAccountResolverTest` must be green (`./mvnw test`).

| Test | Expected |
|---|---|
| New Google user, email not in DB | `User` created with `INVESTOR` role; `OAuthIdentity` created with `provider=GOOGLE` |
| Existing password user, same verified email | `OAuthIdentity` linked to existing `User`; no duplicate user created |
| Repeat Google login, `OAuthIdentity` already exists | Same `User` returned; no new records |
| `email_verified=false` | Rejected with appropriate error |
| Existing `ADMIN` user, same verified email | Google identity linked; role stays `ADMIN` (not downgraded to `INVESTOR`) |
| Existing `ADVISOR` user, same verified email | Google identity linked; role stays `ADVISOR` |
| Conflicting `provider_subject` | Unique constraint prevents duplicate `(GOOGLE, sub)` |

---

## 2. Integration tests — OAuth2 callback flow

All tests in `GoogleOAuthIntegrationTest` must be green. OIDC provider is mocked (WireMock or Spring Security test support).

| Test | Expected |
|---|---|
| Valid Google OIDC callback | Handoff code issued; exchange via `GET /auth/oauth2/token?code=<code>` returns access token |
| Invalid issuer in ID token | Rejected (401 or redirect to error) |
| Invalid audience (wrong client ID) | Rejected |
| Expired ID token | Rejected |
| Invalid state parameter (CSRF) | Rejected |
| `email_verified=false` | Rejected |
| Callback paths do not weaken `/api/**` | `GET /api/v1/ping` without JWT → still 401 |

---

## 3. Integration tests — Account linking persistence

All tests in `OAuthAccountLinkingIntegrationTest` must be green.

| Test | Expected |
|---|---|
| New Google user via OAuth callback | `users` table has new row with `INVESTOR` role; `oauth_identity` table has row with `(GOOGLE, sub)` |
| Existing password user via OAuth callback | `oauth_identity` row created; `users` table unchanged (same ID, same role) |
| Repeat Google login | No duplicate rows in `users` or `oauth_identity` |

---

## 4. Regression — Existing auth flows

All existing tests in `AuthIntegrationTest` must remain green without modification.

| Test | Expected |
|---|---|
| `POST /auth/login` valid credentials | 200, tokens present |
| `POST /auth/login` wrong password | 401 |
| Protected endpoint without token | 401 |
| Protected endpoint with valid token | 200 |
| `POST /auth/refresh` valid refresh token | 200, new access token |
| `POST /auth/logout` → subsequent refresh | 204 then 401 |
| Admin endpoint as non-admin | 403 |

---

## 5. Handoff token exchange

| Test | Expected |
|---|---|
| `GET /auth/oauth2/token?code=<valid-code>` | 200, access token returned |
| `GET /auth/oauth2/token?code=<same-code>` (second use) | 401 (single-use) |
| `GET /auth/oauth2/token?code=<expired-code>` (after 60s) | 401 |
| `GET /auth/oauth2/token?code=<unknown-code>` | 401 |

---

## 6. Demo profile safety

| Check | Expected |
|---|---|
| App starts with demo profile, no Google credentials | Starts without error; `/actuator/health` returns UP |
| `GET /oauth2/authorization/google` without Google credentials | 404 or descriptive error, not a 500 or startup crash |
| Password login works on demo profile alongside OAuth2 config | `POST /auth/login` with admin/admin → 200 |

---

## 7. Manual demo verification

Run against a locally running stack with real Google OAuth2 credentials configured in `.env`.

```bash
# 1. Start the app with demo profile
# Ensure GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REDIRECT_URI are set in .env

# 2. Open in browser
# Navigate to http://localhost:8080/oauth2/authorization/google
# Complete Google sign-in flow

# 3. After redirect, extract the handoff code from the callback URL
CODE=<handoff-code-from-redirect>

# 4. Exchange handoff code for access token
RESP=$(curl -s "http://localhost:8080/auth/oauth2/token?code=$CODE")
ACCESS=$(echo $RESP | jq -r .accessToken)
echo "Access token: $ACCESS"

# 5. Call protected endpoint with Google-issued token
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $ACCESS" \
  http://localhost:8080/api/v1/ping
# Expected: 200

# 6. Verify user record was created
# Check database: SELECT * FROM users WHERE email = '<google-email>';
# Check database: SELECT * FROM oauth_identity WHERE provider = 'GOOGLE';

# 7. Verify password login still works
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq .
# Expected: 200 with accessToken
```

---

## 8. Build gate

```bash
./mvnw verify
```

Must exit 0 with no test failures and no compiler errors before merge.

---

## 9. Security checklist

- [ ] No Google client secret, authorization code, or ID token appears in committed files or logs
- [ ] Handoff code is single-use and expires in < 60 seconds
- [ ] `email_verified=true` is enforced before any account creation or linking
- [ ] ID token issuer, audience, signature, and expiry are all validated
- [ ] Google sign-in cannot create or escalate to `ADMIN` or `ADVISOR` roles
- [ ] `.env.example` has placeholders but no real credential values
