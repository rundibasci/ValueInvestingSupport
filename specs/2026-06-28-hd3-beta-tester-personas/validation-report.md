# HD3 Validation Report

## Docker Demo Run

- Command: `docker compose up -d --build`.
- Backend health: `UP`.
- Health components: PostgreSQL `UP`, Redis `UP`, disk space `UP`, ping `UP`, SSL `UP`.
- Frontend check: `curl.exe -I http://127.0.0.1:5173` returned `HTTP/1.1 200 OK`.
- Backend was rebuilt once during HD3 after the seed transaction fix.
- Final state: `docker compose down` stopped and removed the demo containers after the database dump was persisted.

## Persona Credentials Created

| Persona | Email | Role |
|---|---|---|
| Very prudent value investor | `prudent.beta@localstack.local` | `INVESTOR` |
| Hedge-fund asset allocator | `allocator.beta@localstack.local` | `ADVISOR` |
| Financial journalist / trend observer | `journalist.beta@localstack.local` | `INVESTOR` |

## Persona Workflow Evidence

### Prudent Value Investor

- Seeded: `KO,JNJ,PG`.
- Portfolio: `HD3 Prudent Defensive Portfolio`.
- Holdings: KO 10 shares, JNJ 4 shares.
- Weighted MoS: `-119.51%`.
- Watchlist: MSFT at 15.00% MoS threshold, PG at 12.00%.
- Reviewed: KO, JNJ, PG.

### Hedge-Fund Asset Allocator

- Seeded: `MSFT,NVDA,ADBE`.
- Portfolio: `HD3 Allocator Quality Portfolio`.
- Holdings: MSFT 5 shares, KO 8 shares.
- Weighted MoS: `-182.78%`.
- Watchlist: NVDA at 10.00% MoS threshold, ADBE at 10.00%.
- Reviewed: MSFT, NVDA, KO.

### Financial Journalist / Trend Observer

- Seeded: `MSFT,NVDA,TSLA`.
- Portfolio: `HD3 Journalist Narrative Portfolio`.
- Holdings: MSFT 2 shares, NVDA 3 shares.
- Weighted MoS: `-299.32%`.
- Watchlist: TSLA at 5.00% MoS threshold, KO at 10.00%.
- Reviewed: MSFT, NVDA, TSLA.

## Database Persistence

Database dump:

```text
specs/2026-06-28-hd3-beta-tester-personas/hd3-beta-personas-demo.pgcustom
```

Dump format: PostgreSQL custom format (`pg_dump -Fc`).

Dump size: 64,014 bytes.

Verification queries confirmed:

- 3 persona users.
- 3 HD3 persona portfolios.
- 6 watchlist entries across the persona users.

## Automated Checks

- `backend`: `.\mvnw.cmd -q "-Dtest=SeedServiceTest" test` passed after adding the transaction fix.
- `backend`: `.\mvnw.cmd -q test` passed for the full backend test suite.
- `frontend`: `npm run typecheck` passed.
- `docker`: backend rebuilt successfully after the transaction fix.

## Browser Evidence Limitation

The browser connector was attempted for local React verification but failed to initialize because its runtime metadata lacked a required sandbox field. The fallback validation used HTTP frontend availability plus authenticated API walkthroughs against the running Docker stack.

## Remaining Gaps

- No screenshots were captured.
