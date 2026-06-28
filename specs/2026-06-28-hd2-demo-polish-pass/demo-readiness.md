# HD2 Demo Readiness Notes

## Local Full-Demo Startup

Use the Docker full-demo stack from the project root:

```powershell
docker compose build backend frontend
docker compose up -d
```

After startup, open:

- React app: `http://localhost:5173`
- Backend health: `http://localhost:8080/actuator/health`

Demo login:

- Email: `admin@localstack.local`
- Password: `admin`

Stop the stack when finished:

```powershell
docker compose down
```

## Deterministic Data Expectations

- The `localstack,docker` backend profile seeds local demo users, securities, watchlist entries, a demo portfolio, quotes, valuations, scores, annual fundamentals, ratio history, and review-page data.
- The Docker compose stack sets `MARKET_DATA_SOURCE=yahoo`, but the basic stakeholder walkthrough should use seeded localstack data and should not require live FMP/Yahoo calls or secrets.
- INGR is available as a review-page validation symbol through the seeded localstack universe.

## Stakeholder Walkthrough Checklist

1. Open the React app at `http://localhost:5173`.
2. Sign in as `admin@localstack.local` / `admin`.
3. Confirm dashboard and navigation load without auth errors.
4. Open Seed Universe and confirm the workflow describes shared reference data, not a personal watchlist or portfolio action.
5. Open Screener and verify seeded securities are discoverable with company context.
6. Open `INGR` in-depth review and inspect valuation, cash generation, earnings, debt, graphs, dividends, quality, risk, and next actions.
7. Confirm review-page percentage metrics are human-scaled.
8. Confirm review charts are visible and do not repeat current-year/current-date labels.
9. Confirm watchlist and portfolio actions transition to stable guarded states after success.
10. Open Watchlist, Portfolio, Rebalancing, Dashboard, and Alerts surfaces for consistent copy, badges, and decision-support framing.

## Known Limitations

- Some INGR provider fields remain intentionally unavailable in localstack data, such as quick ratio, interest coverage, detailed dividend history, analyst estimates, and full provider metadata.
- The current validation pass used API-level review checks plus Docker build/start checks. It did not capture new browser screenshots.
- Vite still reports the existing production bundle chunk-size warning; the build succeeds.
