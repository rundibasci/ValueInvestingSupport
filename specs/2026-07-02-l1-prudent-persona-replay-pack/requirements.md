# L1 Requirements - Prudent Persona Replay Pack

## Scope

Phase L1 turns the HD3 Agent 1 prudent-value validation journal into a repeatable replay pack. The replay uses the deterministic 10-symbol set `BRK.B, JNJ, PG, KO, PEP, WMT, MSFT, ADP, UNP, XOM` and records whether the current product can support a conservative research workflow around review packets, data quality, portfolio concentration, and watchlist rationale.

The replay must:

- Seed the 10 symbols when a live backend is available.
- Request each security review packet and capture score availability, valuation availability, quote/source/freshness status, margin of safety, recommendation, and data-quality notes.
- Create a 10-position equal-weight validation portfolio and verify that no single holding breaches the holding concentration threshold.
- Create an oversized KO or JNJ scenario and confirm holding concentration warnings appear.
- Add PG, KO, JNJ, and MSFT to the watchlist with rationale notes, then reload the watchlist to confirm persistence.
- Store replay evidence under the L1 spec directory.
- State clearly that the replay is research workflow evidence only and is not investment advice or an investable model portfolio.

## Exclusions

- Group K, K1, K2, and K3 cloud distribution work is excluded by user instruction.
- No GCP, Terraform, production deployment, commercial compliance hardening, or infrastructure changes.
- No buy/sell/order language and no brokerage or trade execution behavior.
- No committed secrets, JWTs, refresh tokens, provider API keys, or raw provider redistribution payload archives.
- No attempt to guarantee live Yahoo Finance or FMP availability during validation.

## Decisions

| Decision | Rationale |
|---|---|
| Implement L1 as a replay script and evidence pack | The roadmap asks for deterministic replay evidence rather than a new user-facing product surface. |
| Use PowerShell | The repository already has Windows-oriented replay scripts and the local environment is PowerShell. |
| Keep evidence under the spec directory | The output remains tied to the phase and can be reviewed during merge. |
| Support dry-run mode | Validation can prove the replay contract without requiring a running stack or live market-data provider. |

## Assumptions

- The live replay can be run against the local backend at `http://localhost:8080`.
- A persona or demo account exists for live runs, or the caller provides credentials through script parameters.
- Existing APIs support auth, seed, review packets, portfolio creation/holdings/detail, and watchlist add/list behavior.
- Provider limitations or stale/missing data are acceptable replay findings when the workflow records them explicitly.

## Dependencies

- `scripts/i1-persona-replay.ps1`
- `scripts/rd1-agent1-walkthrough.ps1`
- Backend APIs for `/auth/login`, `/api/v1/admin/seed`, `/api/v1/securities/{symbol}/review`, `/api/v1/portfolios`, and `/api/v1/watchlist`.
- Mission principles for conservative defaults, data before opinion, and decision-support boundaries.
