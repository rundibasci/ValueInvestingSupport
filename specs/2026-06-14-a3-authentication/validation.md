# Validation — A3: Authentication

## Definition of done

All items below must pass before this phase can be merged into `main`.

---

## 1. Happy-path curl sequence

Run against a locally running stack (`docker compose up`, then `./mvnw spring-boot:run`).

```bash
# 1. Create an admin user (requires a seed/bootstrap admin — see note below)
curl -s -X POST http://localhost:8080/api/v1/admin/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Password1!","role":"INVESTOR"}' | jq .

# 2. Login
RESP=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"Password1!"}')
ACCESS=$(echo $RESP | jq -r .accessToken)
REFRESH=$(echo $RESP | jq -r .refreshToken)
echo "Access: $ACCESS"

# 3. Call protected endpoint with valid token → expect 200
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $ACCESS" \
  http://localhost:8080/api/v1/ping
# Expected: 200

# 4. Call protected endpoint without token → expect 401
curl -s -o /dev/null -w "%{http_code}" \
  http://localhost:8080/api/v1/ping
# Expected: 401

# 5. Refresh token → expect 200 with new access token
NEW_ACCESS=$(curl -s -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}" | jq -r .accessToken)
echo "New access: $NEW_ACCESS"

# 6. Logout → expect 204
curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/auth/logout \
  -H "Authorization: Bearer $NEW_ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}"
# Expected: 204

# 7. Attempt refresh after logout → expect 401 (token revoked in Redis)
curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}"
# Expected: 401
```

> **Bootstrap admin note:** For local testing, either add a Flyway migration (`V3__seed_admin.sql`) that inserts a BCrypt-hashed admin user, or document a one-time `curl` to a temporarily unprotected seed endpoint. The seed migration must not run in `prod` profile.

---

## 2. MockMvc integration tests

All tests in `AuthIntegrationTest` and `AdminUserIntegrationTest` must be green (`./mvnw test`).

| Test | Expected |
|---|---|
| `POST /auth/login` — valid credentials | 200, body contains `accessToken` and `refreshToken` |
| `POST /auth/login` — wrong password | 401 |
| `POST /auth/login` — unknown username | 401 |
| `GET /api/v1/ping` — no Authorization header | 401 |
| `GET /api/v1/ping` — malformed token | 401 |
| `GET /api/v1/ping` — valid Bearer token | 200 |
| `POST /auth/refresh` — valid refresh token | 200, new `accessToken` present |
| `POST /auth/refresh` — unknown token ID | 401 |
| `POST /auth/logout` — valid token | 204 |
| `POST /auth/refresh` — after logout (revoked) | 401 |
| `POST /api/v1/admin/users` — as ROLE_INVESTOR | 403 |
| `POST /api/v1/admin/users` — as ROLE_ADMIN, valid body | 201 |
| `POST /api/v1/admin/users` — duplicate username | 409 |

---

## 3. Security filter chain smoke checks

- `GET /actuator/health` returns 200 with **no** Authorization header
- `GET /auth/login` (wrong method, GET) returns 405, not 401
- A token issued before logout is no longer accepted **after** logout (stateless access token cannot be revoked, but this is acceptable — access tokens expire in 15 min; only refresh revocation is tested)

---

## 4. Build gate

```bash
./mvnw verify
```

Must exit 0 with no test failures and no compiler errors before merge.
