# RD1-1 Requirements - Yahoo Finance Startup Ingestion Profile

## Scope

- Implement roadmap phase `RD1-1: Yahoo Finance Startup Ingestion Profile`.
- The phase follows `SC2` because it is the first unstarted roadmap phase after the latest merged branch.
- Exclude Group K, K1, K2, and K3 work entirely.
- Provide a local full-stack demo that starts from Docker Compose and ingests a curated Yahoo Finance-backed universe without requiring an FMP API key.

## Decisions

- Use the existing `SeedService` as the source of truth for profile, fundamentals, ratios, quote, valuation, and score creation.
- Use existing job components for the startup quote refresh, dividend update, and alert detection passes.
- Model startup observability as regular `JobRunLog` and `IngestionEvent` records so the existing job control UI can inspect it.
- Use a separate `realDemo` profile rather than changing `localstack` or `demo`.
- Keep cron scheduling disabled by default for `realDemo`; the startup runner provides the deterministic first-pass demo state.

## Assumptions

- The current H2 `demo` profile is too narrow for the full-feature real demo; PostgreSQL and Redis are required.
- The Docker backend image can run from the existing backend Dockerfile without repo-level build changes.
- Yahoo Finance coverage can be incomplete. Per-symbol failures must be logged and visible without aborting the entire startup sequence.
- `BRK-B` is the platform ticker spelling used in roadmap defaults; any provider-specific normalization remains inside the existing market-data client.

## Dependencies

- Existing market-data abstraction and Yahoo Finance implementation.
- Existing seed pipeline, valuation, scoring, quote refresh, dividend update, alert detection, job logging, and ingestion event repositories.
- Docker Compose with PostgreSQL 16 and Redis 7.

## Exclusions

- No GCP, Cloud Run, Terraform, Secret Manager, Scheduler, IAM, or production deployment work.
- No new financial advice language or order workflow.
- No paid FMP dependency for this phase.
- No screenshot walkthrough; that belongs to `RD1-2`.
