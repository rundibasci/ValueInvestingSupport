# Validation — Phase LS2: HTML Demo Client

## How to Know It's Done

### Pre-conditions

- Docker Redis is running (`docker compose -f docker-compose.demo.yml up -d`)
- App started with demo profile: `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`

### Step 1 — URL banner in console

The terminal where the app started must print:

```
>>> Demo ready at http://localhost:8080/demo.html
```

before or just after the `Started ... in X seconds` Spring line.

### Step 2 — Login

1. Open `http://localhost:8080/demo.html` in a browser.
2. Enter `admin` / `admin` and click **Login**.
3. Expected: green success message ("Logged in as admin"), JWT snippet visible (truncated).
4. Not expected: any error, 401, or blank panel.

### Step 3 — Ping Admin

1. Click **Ping Admin**.
2. Expected response panel shows:
   - HTTP status: `200`
   - Role: `ADMIN`
   - X-Cache: any value (hit/miss/n-a — just must be rendered, not blank)
3. Not expected: 401 Unauthorized, 403 Forbidden, or JS console errors.

### Step 4 — Server stays up

After completing steps 1–3, wait 30 seconds and reload `demo.html`.
The page loads normally — the server has not exited.

### Step 5 — Wrong credentials

Enter username `baduser` / password `wrong` and click **Login**.
Expected: red error message ("Login failed" or similar). No unhandled JS exception.

## Merge Criteria

All 5 steps pass with no browser console errors and no backend exceptions in the log.
