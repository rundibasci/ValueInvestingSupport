# Tech Stack — Value Investing Advisory Platform

## Data Source Strategy

| Phase | Source | Cost | Why |
|---|---|---|---|
| **Demo / Prototype** | Yahoo Finance (unofficial public API) | Free | Zero cost, sufficient for single-stock demo; no bulk/screener |
| **Production MVP** | Financial Modeling Prep (FMP) Premium | $49/mo | Official, bulk APIs, 30y history, Piotroski, full screener |

The Valuation Engine and Value Score Engine are **data-source agnostic** — they operate on domain entities (`FundamentalSnapshot`, `RatioSnapshot`) regardless of where the data came from. Only the data client layer changes between demo and production.

## System Layers

```
┌─────────────────────────────────────────────────────────┐
│                    FRONTEND                             │
│  React 18 + TypeScript 5 + TailwindCSS 3               │
│  Recharts (charts) | TanStack Query (server state)      │
│  Vite (build) | React Router v6                         │
│  Shared universe seed UI | Market research UI           │
└────────────────────────┬────────────────────────────────┘
                         │ REST / JSON
┌────────────────────────▼────────────────────────────────┐
│                    BACKEND (API Gateway)                 │
│  Spring Boot 3.x + Java 21                              │
│  Spring Security (JWT RS256) | Spring Cache (Redis)      │
│  Spring Data JPA (Hibernate 6) | Flyway (migrations)    │
│  Spring Scheduler (batch jobs)                          │
│  Shared market-universe seed and research APIs           │
└──────┬─────────────────┬──────────────────┬─────────────┘
       │                 │                  │
┌──────▼──────────┐  ┌───▼───────────┐  ┌──▼──────────────────┐
│  Market Data    │  │ Valuation     │  │  Portfolio          │
│  Client         │  │ Engine        │  │  Engine             │
│  [Demo]  Yahoo  │  │ DCF/Graham/   │  │  Allocation +       │
│  [Prod]  FMP    │  │ DDM/Score     │  │  Rebalancing        │
└──────┬──────────┘  └───────────────┘  └─────────────────────┘
       │
┌──────▼──────────────────────────────────────────────────┐
│                    DATA LAYER                           │
│  PostgreSQL 16 (fundamentals, portfolios, users)        │
│  Redis 7 (cache: API responses, DCF results)            │
│  PostgreSQL partitioned tables (price history)          │
└─────────────────────────────────────────────────────────┘
```

## Backend

| Concern | Choice | Notes |
|---|---|---|
| Language | Java 21 | Records, pattern matching, virtual threads |
| Framework | Spring Boot 3.x | Auto-configuration, actuator, scheduler |
| HTTP Server | Tomcat (embedded) | Default Spring Boot |
| Security | Spring Security + JWT RS256 | Access token 15 min, refresh 7 days |
| ORM | Spring Data JPA / Hibernate 6 | Entities + repositories |
| Migrations | Flyway | Versioned SQL scripts in `db/migration/` |
| HTTP Client | Spring WebClient | Reactive, non-blocking; used for FMP calls |
| Caching | Spring Cache + Redis | `@Cacheable` annotations with TTL config |
| Batch/Jobs | Spring `@Scheduled` | Nightly bulk sync, 15-min quote refresh |
| Build | Maven (or Gradle) | TBD when scaffolding |

## Frontend

| Concern | Choice | Notes |
|---|---|---|
| Language | TypeScript 5 | Strict mode enabled |
| Framework | React 18 | Functional components, hooks |
| Build | Vite | Fast HMR, ESM |
| Styling | TailwindCSS 3 | Utility-first; no CSS modules |
| Charts | Recharts | Financial time-series, bar, pie |
| Server State | TanStack Query (React Query v5) | Caching, refetch, invalidation |
| Routing | React Router v6 | File-based routes |
| Forms | React Hook Form | Screener filters, DCF custom inputs |

### Frontend Product Surfaces

| Surface | Requirement |
|---|---|
| Shared universe seeding | Authenticated investors, advisors, and admins can seed ticker CSV lists; admins can also manage named seed packs and broader universe maintenance. Seeded securities become shared reference data discoverable by every authenticated user. |
| Market-wide research | Screener/search UI works across the seeded universe and shows business context in result rows: symbol, company name, sector, exchange, country when available, and a concise description/profile excerpt. |
| Single-stock research packet | Dedicated review route `/securities/:symbol/review` exposes DCF, free cash flow, Graham number, margin of safety, earnings, debt, dividend sustainability, dividend yield, quick ratio when available, valuation scenarios, recommendation, source coverage/freshness, and data-availability labels. |

## Data

| Store | Version | Purpose |
|---|---|---|
| PostgreSQL | 16.x | Primary relational store: securities, fundamentals, portfolios, users, alerts |
| Redis | 7.x | Cache layer: FMP API responses, computed DCF, screener results |
| PostgreSQL partitioned | 16.x | `price_quote` table partitioned by month for time-series queries |

### Data Model Notes

- Securities, company profiles, fundamentals, ratios, quotes, valuations, and scores are platform-wide reference data, not duplicated per user.
- Users own watchlists, portfolios, holdings, alert thresholds, and account/session state.
- `security.description` / provider profile text should be populated and exposed through search/screener/detail APIs when available.
- Ratio and financial-health DTOs should expose liquidity and dividend-coverage metrics already present in the data model, including current ratio, quick ratio when available from provider data, payout ratio, debt-to-equity, and dividend yield.

## External Data Sources

### Yahoo Finance (Demo / Free)

| Parameter | Value |
|---|---|
| Provider | Yahoo Finance (unofficial public API — no key required) |
| Quote URL | `https://query1.finance.yahoo.com/v8/finance/chart/{symbol}` |
| Fundamentals URL | `https://query1.finance.yahoo.com/v10/finance/quoteSummary/{symbol}?modules=…` |
| Key modules used | `financialData`, `defaultKeyStatistics`, `incomeStatementHistory`, `balanceSheetHistory`, `cashflowStatementHistory`, `assetProfile`, `summaryDetail` |
| Auth | None (public, unofficial) |
| Rate limit | ~100 req/min informally; no SLA |
| Limitations | No bulk API, no Piotroski score, no screener endpoint, data may lag, TOS not for commercial redistribution |

> **Use only for demo/prototype.** Do not rely on Yahoo Finance in production.

### FMP — Financial Modeling Prep (Production)

| Parameter | Value |
|---|---|
| Provider | Financial Modeling Prep |
| Base URL | `https://financialmodelingprep.com/stable/` |
| Auth | Header `apikey: {API_KEY}` |
| Recommended Plan | Premium ($49/mo) — 300 req/min, 30y history, bulk APIs |
| Key | Stored in environment variable `FMP_API_KEY`, never in code |

## Secrets & Local Configuration

All credentials are kept out of version control. Two complementary mechanisms are in use:

### `.env` (local runtime)
Copy `.env.example` → `.env` (listed in `.gitignore`). Spring Boot loads it via `spring.config.import=optional:file:.env[.properties]` or the IDE environment injection. Variables:

```
SPRING_PROFILES_ACTIVE=local
MARKET_DATA_SOURCE=fmp
FMP_API_KEY=<your-key>
DATABASE_URL=jdbc:postgresql://localhost:5432/vis
DATABASE_USERNAME=vis
DATABASE_PASSWORD=vis
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_PRIVATE_KEY=
JWT_PUBLIC_KEY=
```

### `application-fmpkey.yml` (test profile)
`backend/src/test/resources/application-fmpkey.yml` — gitignored via `**/application-fmpkey.yml`. Activate on integration test classes that call FMP directly:

```java
@ActiveProfiles({"test", "fmpkey"})
```

Content:
```yaml
fmp:
  api-key: <your-key>
market-data:
  source: fmp
```

> **Rule:** the actual key value must never appear in any committed file — not in `application.yml`, not in test fixtures, not in comments.

## Infrastructure & DevOps

| Concern | Choice |
|---|---|
| Local dev | Docker Compose (PostgreSQL + Redis) |
| Secrets | `.env` (runtime) + `application-fmpkey.yml` (tests) — both gitignored; never committed |
| Logging | Logback → structured JSON → ELK (production) |
| Metrics | Micrometer → Prometheus → Grafana |
| Health | Spring Boot Actuator `/actuator/health` |

## GCP Distribution

The application is deployed progressively. Cloud Run is the default compute choice: the HTTP API is stateless, while PostgreSQL and Redis retain durable/cache state. Kubernetes is not required for the MVP target.

| Phase | GCP design | Key constraint |
|---|---|---|
| **K1 - Stakeholder Cloud Deployment** | Cloud Run service for the Spring Boot API and static demo pages; Cloud SQL for PostgreSQL; Memorystore for Redis; Secret Manager; Cloud Logging/Monitoring. | A single controlled API instance may retain the existing `@Scheduled` jobs temporarily; this is internal-only and must not scale background work. |
| **K2 - Production-Shaped GCP Platform** | Cloud Run API service; Cloud Run Jobs invoked by Cloud Scheduler; Cloud SQL private connectivity/backups/PITR; Memorystore; Artifact Registry; Secret Manager; Cloud Monitoring; custom HTTPS domain; Terraform-managed environments. | Never run scheduled work in multiple API instances. Jobs must be independently triggerable, idempotent, observable, and retry-safe. |
| **K3 - Commercial & Compliance Hardening** | K2 plus edge/rate protection, audit controls, security scanning, alerting, restore/failure exercises, data-residency controls, and operational runbooks. | Release depends on verified FMP display rights and GDPR/MiFID II obligations, not infrastructure completion alone. |

### Infrastructure as Code

Terraform becomes the source of truth from K2 onward. It manages resource configuration, IAM, networking, scheduler definitions, monitoring, and Secret Manager **references**. Secret values are created and rotated outside Terraform state and are injected at runtime.

### Runtime Responsibilities

| Component | GCP service | Responsibility |
|---|---|---|
| HTTP API | Cloud Run service | Authenticated REST endpoints, React/static content while appropriate, and Actuator health/metrics. |
| Background work | Cloud Run Jobs + Cloud Scheduler | Ingestion, quote refresh, dividends, insider data, alert detection, and bounded retry execution. |
| Primary data | Cloud SQL for PostgreSQL | Immutable financial snapshots, portfolios, users, alerts, Flyway migrations, backups, and point-in-time recovery. |
| Cache/token state | Memorystore for Redis | Market-data caches, computed cache entries, refresh-token lifecycle, and rate-safe cache-first behaviour. |
| Secrets | Secret Manager | FMP key, JWT key material, SMTP credentials, Google OAuth credentials, and service configuration requiring confidentiality. |
| Artefacts and telemetry | Artifact Registry; Cloud Logging/Monitoring | Immutable container images, deployment provenance, structured logs, metrics, dashboards, and alerts. |

## Key Environment Variables

```
# Data source (set to "yahoo" for demo, "fmp" for production)
MARKET_DATA_SOURCE   yahoo | fmp

# FMP (production only)
FMP_API_KEY          FMP API key

# Database
DATABASE_URL         PostgreSQL JDBC URL
REDIS_HOST           Redis hostname
REDIS_PORT           Redis port (default 6379)

# Auth (not needed for demo milestone)
JWT_PRIVATE_KEY      RS256 private key (PEM)
JWT_PUBLIC_KEY       RS256 public key (PEM)
```
