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

                (interpretation only, never computation)
       ┌───────────────────────────────────┐
       │ AI Investment Thesis Client       │
       │ (Backend, calls out — not on the  │
       │ request path of any deterministic │
       │ calculation above)                │
       └─────────────────┬─────────────────┘
                         │ Vertex AI REST/gRPC, service-account auth
       ┌───────────────────▼───────────────────┐
       │ Google Cloud Vertex AI — Gemini       │
       │ (external managed API, no self-hosted │
       │ weights, no grounding tools enabled)  │
       └───────────────────────────────────────┘
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
| Score and data-quality transparency | Seed results, review pages, screener/search rows, and portfolio holdings show structured score availability and data-quality states: available, stale, pending, provider-limited, missing seeded history, internal computation missing, or guardrail-blocked. |
| Portfolio exposure warnings | Portfolio detail and add-to-portfolio flows show holding and sector concentration when data is available. Warnings explain exposure without issuing buy/sell instructions. |
| Watchlist rationale | Watchlist items support a concise user note and monitoring reason, including "wait for better price", valuation concern, data-quality gap, dividend concern, or narrative catalyst. |
| Screener diagnostics | Empty screener results explain which filters likely eliminated candidates and provide filter-relaxation suggestions while preserving the user's current criteria. |
| Cross-symbol comparison | Users can compare selected symbols on MoS, value score, quality, leverage/liquidity, growth, dividend indicators, and source/data coverage, with missing metrics visible per row or cell. |
| AI Investment Thesis | On the Security Review page, an on-demand (never automatic) "Generate AI Thesis" panel calls out to Vertex AI Gemini; shows not-yet-generated, generating, ready, human-review-pending (visually distinct, never presented as a finished recommendation), stale, failed, and rate-limited states; carries model/prompt provenance and the MiFID II disclaimer. An ADMIN-only review queue lists `HUMAN_REVIEW_PENDING` theses across symbols. See `specs/roadmap.md` → Group TA (Phase TA5). |

## Data

| Store | Version | Purpose |
|---|---|---|
| PostgreSQL | 16.x | Primary relational store: securities, fundamentals, portfolios, users, alerts |
| Redis | 7.x | Cache layer: FMP API responses, computed DCF, screener results |
| PostgreSQL partitioned | 16.x | `price_quote` table partitioned by month for time-series queries |

### Data Model Notes

- Securities, company profiles, fundamentals, ratios, quotes, valuations, and scores are platform-wide reference data, not duplicated per user.
- Users own watchlists, portfolios, holdings, alert thresholds, and account/session state.
- Watchlist items may store user-authored rationale notes and monitoring categories; these remain user-owned and are not platform-wide reference data.
- `security.description` / provider profile text should be populated and exposed through search/screener/detail APIs when available.
- Ratio and financial-health DTOs should expose liquidity and dividend-coverage metrics already present in the data model, including current ratio, quick ratio when available from provider data, payout ratio, debt-to-equity, and dividend yield.
- API DTOs that expose scores, valuations, and provider-backed metrics should include structured availability metadata so the frontend can distinguish stale data, provider limitation, missing seeded history, missing internal computation, and calculation guardrail failures without parsing display text.
- Portfolio read models should include computed holding weights and sector weights when price and sector data are available, enabling concentration warnings without duplicating ownership state.
- `investment_thesis_result` (Group TA) is platform-wide reference data, like `ValuationResult`/`ValueScore`, not user-owned: one row per generation, keyed by security, storing the pinned model/prompt version, the exact input snapshot sent to Gemini, the parsed output fields, `status` (`READY`/`FAILED`/`HUMAN_REVIEW_PENDING`), and `generatedAt`. Rows are never overwritten in place (consistent with mission.md's immutable-history principle); a regeneration inserts a new row and the API serves the latest one plus a `stale` flag when underlying valuation/score inputs have since refreshed.

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

### Vertex AI — Gemini (AI Investment Thesis Engine)

| Parameter | Value |
|---|---|
| Provider | Google Cloud Vertex AI — Gemini (managed API; no self-hosted weights, no adapter) |
| Model | `GEMINI_MODEL_ID` — a specific **pinned, stable version string, never a floating/auto-updating alias** (a mutable alias can change server-side outside VIS's control, silently invalidating the TA3 benchmark's conclusions). Initial candidate: a current-generation Gemini Flash-tier model, with Pro-tier as fallback if Flash-tier fails the TA3 gate. Chosen and gated in `specs/roadmap.md` → Group TA (Phase TA3); track Google's deprecation/EOL notice for the pinned version and re-run the TA3 gate before migrating to a newer one. |
| Auth | Service account via Application Default Credentials, injected through Secret Manager / local `.env`-referenced key file — never a static API key committed to source |
| Region | `VERTEX_AI_LOCATION` — data-residency decision recorded in Group TA's governance review (Phase TA1) |
| Decoding | `temperature: 0` (the API's deterministic/greedy-equivalent setting) and a fixed `maxOutputTokens`. Vertex AI does not guarantee bit-exact determinism across identical calls the way local greedy decoding did in TRAIN-03 — this is a documented caveat, not an assumption of full reproducibility. |
| Structured output | `responseMimeType: application/json` + `responseSchema` bound to `vis-model-training/schemas/thesis-output.schema.json`, enforcing schema-conforming JSON at the API level |
| Grounding tools | Disabled — no Search grounding, no Vertex AI Search, no function calling to external data; the model reasons only over VIS-supplied financial context |
| Role | Interpretation and narrative synthesis only (bull case, bear case, risks, invalidation conditions); never computes DCF/Graham/DDM/Margin of Safety/Value Score and never issues `BUY`/`SELL`/`HOLD` |
| Generation trigger | On-demand only, per symbol, via `POST /api/v1/securities/{symbol}/thesis/generate`; never automatic on page load. Rate-limited per user via `THESIS_GENERATION_DAILY_LIMIT`. Results are persisted (`investment_thesis_result`) and reused until explicitly regenerated. |
| Origin | Supersedes the local `google/gemma-3-27b-it` teacher + `google/gemma-3-4b-it` QLoRA adapter path (`vis-model-training/`), closed `NO_GO` 2026-08-24 after failing its output-quality capability gate — see `vis-model-training/reports/teacher/train-05-failure-and-qlora-pause.md` |
| Reused from `vis-model-training/` | Input/output schemas, system prompt contract, dataset/response validator CLI (TRAIN-02), benchmark harness and 50-case dataset (TRAIN-03), 500-scenario generator/dataset (TRAIN-04), runtime-contract design (TRAIN-12) |
| Fallback | Deterministic only — on error, timeout, or non-conforming output after retries: persisted `status=FAILED`, `classification: UNDER_REVIEW`, `humanReviewRequired: true`, no bull/bear case, tracked error reason; never a silent second-model fallback |
| Test isolation | Default backend tests mock `InvestmentThesisClient` and never call live Vertex AI; live-API integration tests run only under `@ActiveProfiles({"test","vertexkey"})` against `application-vertexkey.yml` (gitignored, mirroring `application-fmpkey.yml`) and are excluded from the default CI run |

> **Use only when the Phase TA3 capability benchmark has passed**, including its real-ticker knowledge-leakage check. Gemini responses reach a user only after the unchanged TRAIN-02 validator accepts them; the feature stays behind `THESIS_AGENT_ENABLED=false` until that gate is met.

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
THESIS_AGENT_ENABLED=false
GOOGLE_CLOUD_PROJECT=
VERTEX_AI_LOCATION=
GEMINI_MODEL_ID=
GOOGLE_APPLICATION_CREDENTIALS=
THESIS_GENERATION_DAILY_LIMIT=5
```

### Vertex AI service-account key (local runtime)
`GOOGLE_APPLICATION_CREDENTIALS` points to a local service-account JSON key file. Group TA must add an explicit `.gitignore` rule for this key file (matching the existing `.env` / `**/application-fmpkey.yml` pattern) before it is ever created locally — never committed, never logged. In deployed environments (K1+) the equivalent identity is provided by a GCP service account bound to Cloud Run, with the key material itself never leaving Secret Manager/IAM.

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

### `application-vertexkey.yml` (test profile)
`backend/src/test/resources/application-vertexkey.yml` — gitignored via `**/application-vertexkey.yml`, mirroring the FMP pattern above. Activate only on the small set of integration tests that call the real Vertex AI API:

```java
@ActiveProfiles({"test", "vertexkey"})
```

Every other backend test — including the default CI run — mocks `InvestmentThesisClient` instead of hitting Vertex AI, keeping the default suite free, deterministic, and network-independent.

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
| Secrets | Secret Manager | FMP key, JWT key material, SMTP credentials, Google OAuth credentials, Vertex AI service-account key, and service configuration requiring confidentiality. |
| Artefacts and telemetry | Artifact Registry; Cloud Logging/Monitoring | Immutable container images, deployment provenance, structured logs, metrics, dashboards, and alerts. |
| AI Investment Thesis | Vertex AI (Gemini, managed API) | Interpretation-only narrative synthesis (bull/bear case, risks, invalidation conditions) over VIS-computed financial context; no deterministic calculation, no autonomous data retrieval, deterministic fallback on error/timeout (Group TA). Reachable independently of Cloud Run deployment status. |

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

# AI Investment Thesis (Vertex AI / Gemini — Group TA)
THESIS_AGENT_ENABLED         true | false (default false until the TA3 capability gate passes)
GOOGLE_CLOUD_PROJECT         GCP project id hosting Vertex AI
VERTEX_AI_LOCATION           Vertex AI region (data-residency decision, see tech-stack.md → Vertex AI table)
GEMINI_MODEL_ID              Pinned Gemini model id/version (stable version string, never a floating alias)
THESIS_GENERATION_DAILY_LIMIT  Per-user daily cap on thesis generation (default 5)
```
