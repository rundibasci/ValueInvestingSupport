# Real Demo Guide

## Start

Run from the repository root:

```powershell
docker compose -f docker-compose.realDemo.yml up --build
```

The backend starts with the `realDemo` Spring profile, PostgreSQL, Redis, and `MARKET_DATA_SOURCE=yahoo`.

## Seeded Universe

Default startup symbols:

```text
AAPL,MSFT,KO,JNJ,PG,PEP,WMT,BRK-B,UNP,XOM
```

Override them before startup with `REAL_DEMO_TICKERS`, for example:

```powershell
$env:REAL_DEMO_TICKERS="KO,JNJ,PG"
docker compose -f docker-compose.realDemo.yml up --build
```

## Demo Accounts

- Admin: `admin@realdemo.local` / `admin`
- Investor: `investor@realdemo.local` / `admin`

These accounts are local-demo defaults only. Do not use them outside the `realDemo` profile.

## Expected Startup

- PostgreSQL and Redis usually become healthy in less than 30 seconds.
- Backend image build depends on Maven dependency cache.
- Yahoo Finance startup ingestion can take several minutes for the default 10-symbol universe.
- Each symbol is seeded independently; one provider failure does not stop the rest of the universe.

## Verification

- Frontend: `http://localhost:5173`
- Backend health: `http://localhost:8080/actuator/health`
- Job control should show a `real-demo-startup` run with seed events and follow-up quote, dividend, and alert activity.

## Yahoo Finance Limitations

Yahoo Finance is an unofficial, zero-cost demo source. It can return partial data, rate-limit calls, omit dividend history, or change response shape without notice. Treat this profile as stakeholder demo evidence, not a production data source.
