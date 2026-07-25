# K1 — Validation

## Acceptance Criteria

- [ ] `Dockerfile` builds successfully; image runs the Spring Boot backend with `k1` profile.
- [ ] `application-k1.yml` connects to Cloud SQL via SocketFactory and Memorystore Redis.
- [ ] `k1-bootstrap.ps1` creates all GCP resources idempotently; running it twice produces no errors.
- [ ] All five Secret Manager secrets exist with version `1` enabled; runtime service account can access them.
- [ ] `k1-deploy.ps1` publishes an immutable image to Artifact Registry and deploys to Cloud Run.
- [ ] Cloud Run service has max-instances=1 at both service and revision level.
- [ ] Secrets are injected from Secret Manager; no secret values appear in image layers, environment variable listings, or logs.
- [ ] `/actuator/health` returns 200 with UP status (anonymous).
- [ ] `/actuator/health/liveness` and `/actuator/health/readiness` probes pass.
- [ ] Flyway migrations apply successfully against Cloud SQL PostgreSQL.
- [ ] Authenticated read-only workflow completes: login → seed (if empty) → quick-analysis → response contains expected fields.
- [ ] MiFID II disclaimer is present on all valuation/score/recommendation responses.
- [ ] `k1-validate.ps1` passes its health, revision, and workflow checks.
- [ ] `k1-rollback.ps1` routes traffic to a specified revision and confirms the rollback.
- [ ] Uptime check is configured on `/actuator/health`.
- [ ] Alert policies exist for the five required conditions.
- [ ] Structured logs are visible in Cloud Logging.
- [ ] `runbook.md` covers all documented procedures and troubleshooting scenarios.

## Backend / Build Verification

| Scenario | Expected result |
|---|---|
| `./mvnw test` (backend) | All existing tests pass; no regressions |
| `npm run typecheck` (frontend) | No type errors |
| `npm run build` (frontend) | Production build succeeds |
| `docker build -t vis-k1 .` | Image builds without errors |
| `docker run --rm -e SPRING_PROFILES_ACTIVE=k1 vis-k1` | Application starts (may fail on Cloud SQL connection — expected without GCP) |
| `git diff --check` | No whitespace errors |

## GCP Resource Validation

| Resource | Check | Expected |
|---|---|---|
| Artifact Registry | Repository `vis-k1` exists in target region | Format: DOCKER |
| Service Account | `vis-k1-runtime@<project>.iam.gserviceaccount.com` exists | Roles: cloudsql.client, monitoring.metricWriter, logging.logWriter |
| Cloud SQL | Instance `vis-k1-postgres` exists | PostgreSQL 16, db-f1-micro, 10 GB SSD |
| Cloud SQL | Database `vis` exists | Accessible via `vis_app` user |
| Memorystore | Instance `vis-k1-redis` exists | Redis 7.0, 1 GB, basic tier |
| VPC Connector | `vis-k1-connector` exists in target region | Min 2, max 3 instances, /28 range |
| Secret Manager | Five secrets exist with version `1` ENABLED | `vis-k1-database-password`, `vis-k1-fmp-api-key`, `vis-k1-jwt-private-key`, `vis-k1-jwt-public-key`, `vis-k1-smtp-password` |
| Cloud Run | Service `value-investing-support-k1` deployed | Max instances 1, gen2, 1 vCPU, 1 GiB |

## Cloud Run Service Validation

| Scenario | Expected result |
|---|---|
| `GET /actuator/health` (anonymous) | 200, status UP, no component details exposed |
| `GET /actuator/health` (authenticated admin) | 200, status UP, component details visible |
| `GET /actuator/health/liveness` | 200 |
| `GET /actuator/health/readiness` | 200 |
| `POST /auth/login` with valid credentials | 200, JWT access + refresh tokens |
| `GET /api/v1/searches?q=AAPL` (authenticated) | 200 (or 404 if not seeded — not a failure) |
| `POST /api/v1/admin/seed` with AAPL (ADMIN) | 200, seed result with source=provider |
| `GET /api/v1/securities/AAPL/quick-analysis` | 200, valuation + MoS + disclaimer |
| Max instances check | `gcloud run services describe` shows `max-instances: 1` |
| Revision max instances check | `gcloud run revisions describe` shows `max-instances: 1` |
| Structured log entries visible | `gcloud logging read` returns application log entries |

## Secret Safety Checks

| Scenario | Expected result |
|---|---|
| Docker image inspection (`docker history`) | No secret values in layers |
| Cloud Run env var listing | No secret values (only env vars, not secrets) |
| Cloud Logging query for secrets | No API keys, passwords, or private keys in log payloads |
| `gcloud secrets versions access` by unauthorized principal | Permission denied |
| `gcloud secrets versions access` by runtime SA | Returns secret value |

## Regression Checks

- [ ] All existing backend tests pass (no K1 changes to application logic).
- [ ] Frontend typecheck and build pass.
- [ ] Existing local Docker Compose workflow still functions (K1 profile is additive).
- [ ] `application-local.yml` behavior is unchanged.
- [ ] Flyway migrations apply cleanly on a fresh Cloud SQL database.
- [ ] Yahoo Finance fallback still works when FMP is unavailable (existing behavior, unchanged by K1).
- [ ] No committed file contains a secret, key, password, or credential.

## Verification Commands

```bash
# Backend
cd backend && ./mvnw test

# Frontend
cd frontend && npm test -- --run
cd frontend && npm run typecheck
cd frontend && npm run build

# Docker image
docker build -t vis-k1 .
docker run --rm -e SPRING_PROFILES_ACTIVE=k1 vis-k1

# Git hygiene
git diff --check
git status --short
```

## Manual Validation (GCP live deployment)

1. Run `./deploy/k1/k1-bootstrap.ps1 -ProjectId <id> -Region <region>` — confirm all resources created.
2. Create Cloud SQL user `vis_app` manually in Console; store password as `vis-k1-database-password` version `1`.
3. Load remaining secrets: `gcloud secrets versions add vis-k1-fmp-api-key --data-file <path>`, etc.
4. Run `./deploy/k1/k1-deploy.ps1 -ProjectId <id> -Region <region> -CloudSqlInstance '<conn>' -RedisHost '<ip>'` — confirm revision deployed.
5. Open Cloud Run URL in browser: confirm login page loads.
6. Run `./deploy/k1/k1-validate.ps1 -ProjectId <id> -Region <region>` — confirm all checks pass.
7. Seed a known ticker, run quick analysis — confirm MiFID II disclaimer visible.
8. Trigger a deliberate rollback: `./deploy/k1/k1-rollback.ps1 -ProjectId <id> -Region <region> -Revision <known-good>` — confirm traffic routed.
9. Check Cloud Logging for structured application logs.
10. Verify uptime check and alert policies are active.

## Merge Gate

The phase can be merged when:

- All acceptance criteria pass (script-based checks at minimum; live GCP checks where access permits).
- Backend tests, frontend typecheck, and frontend build pass with no new failures.
- Docker image builds and starts successfully.
- No secrets, keys, passwords, or credentials appear in any committed file, image layer, env-var listing, or log output.
- `git diff --check` passes; no unrelated files changed.
- `runbook.md` is complete and covers all documented procedures.
- If live GCP deployment was performed, sanitized validation evidence is captured (revision, image digest, health, Flyway version, max-instances config, known limitations).
