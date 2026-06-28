# HD2 Validation Report

## Automated Checks

- `frontend`: `npm run typecheck` passed.
- `frontend`: `npm run build` passed.
  - Note: Vite reports the existing chunk-size warning for a large JS bundle.
- `backend`: `.\mvnw.cmd "-Dtest=SeedServiceTest,SecurityReviewServiceTest" test` passed.
- `backend`: `.\mvnw.cmd -q test` passed.
- `docker`: `docker compose build backend frontend` passed.
- `docker`: `docker compose up -d` started PostgreSQL, Redis, backend, and frontend successfully.
- `docker`: backend health returned `UP` with PostgreSQL and Redis components healthy.
- `docker`: frontend returned `HTTP 200` at `http://127.0.0.1:5173`.

## Localstack Walkthrough Evidence

Authenticated API walkthrough against the Docker stack:

```json
{"health":"UP","reviewSymbol":"INGR","annualCount":5,"ratioCount":10,"watchlistCount":4,"frontendStatus":"200"}
```

This confirms:

- Localstack credentials work for `admin@localstack.local` / `admin`.
- INGR review data is available without live provider secrets.
- Review response includes annual fundamentals and 10 ratio points.
- Seeded watchlist data is available to the authenticated user.
- The frontend route is served by nginx.

## Fix Coverage

- Current/TTM seed-generated fundamentals are replaced during reseeding instead of appended.
- Current-year annual ratios and TTM ratios are replaced during reseeding instead of appended.
- Review-page ratio charts now use annual ratio history, avoiding duplicate current-date TTM/annual chart rows.
- Review-page percentage formatting is field-aware:
  - Decimal ratios are multiplied for display.
  - Already-percent values such as MoS and CAGR remain unchanged.
- Watchlist add success updates the React Query cache and invalidates the watchlist query.
- Portfolio add success no longer displays alongside the existing-holding state after refetch.
- Review charts use explicit responsive dimensions.
- Docker backend build retains Maven wrapper normalization for Windows checkout compatibility.

## Remaining Gaps

- No new browser screenshot evidence was captured in this phase.
- The first PowerShell frontend probe after container start returned a null-reference error, but `curl.exe -I http://127.0.0.1:5173` returned `HTTP 200`; this appears to be a probe issue, not an app failure.
- Existing build warning: Vite reports a JS bundle over 500 kB after minification.
