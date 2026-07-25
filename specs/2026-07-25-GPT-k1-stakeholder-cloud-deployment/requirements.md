# K1 — Stakeholder Cloud Deployment

## Context

Groups A–RH are complete — the platform delivers valuation, scoring, moat analysis, portfolio intelligence, professional workflows, and universe curation on local/containerized infrastructure. Group K moves the application to Google Cloud for internal stakeholder evaluation before production shaping (K2) and commercial hardening (K3).

K1 is deliberately bounded: one Cloud Run service backed by non-production managed PostgreSQL and Redis, with the existing in-process `@Scheduled` jobs tolerated temporarily. This is an internal-only environment — not a customer-facing production release.

The platform remains a decision-support tool. All fair value, MoS, score, and recommendation outputs retain the MiFID II disclaimer regardless of where the application runs.

## Scope

### Containerization

- Root `Dockerfile` building a single Spring Boot image from the existing Maven project.
- Multi-stage build: Maven compile/package in a builder stage, slim JRE runtime stage.
- Expose port 8080; include health probes at `/actuator/health`.
- Immutable image tagged by short Git commit SHA; published to Artifact Registry.

### GCP resource provisioning (idempotent bootstrap script)

- `deploy/k1/k1-bootstrap.ps1` (PowerShell 7) that creates:
  - Regional Artifact Registry repository `vis-k1`.
  - Runtime service account `vis-k1-runtime` with `roles/cloudsql.client`, `roles/monitoring.metricWriter`, `roles/logging.logWriter`.
  - PostgreSQL 16 Cloud SQL instance `vis-k1-postgres` (db-f1-micro, 10 GB SSD, zonal, no auto-increase).
  - Database `vis` on the Cloud SQL instance.
  - Serverless VPC Access connector `vis-k1-connector` (min 2, max 3 instances, `/28` range).
  - Basic Redis 7 Memorystore instance `vis-k1-redis` (1 GB, basic tier).
  - Empty Secret Manager secrets: `vis-k1-database-password`, `vis-k1-fmp-api-key`, `vis-k1-jwt-private-key`, `vis-k1-jwt-public-key`, `vis-k1-smtp-password`.
  - IAM binding granting the runtime service account `roles/secretmanager.secretAccessor` on each secret.
- Script is idempotent for named resources.
- Cloud SQL user creation is intentionally manual (password never passes through script arguments).
- Enable required GCP APIs: `artifactregistry`, `cloudbuild`, `run`, `sqladmin`, `redis`, `secretmanager`, `vpcaccess`, `monitoring`.

### Spring Boot K1 profile

- `application-k1.yml` configuring:
  - Cloud SQL PostgreSQL over Unix socket via Cloud SQL SocketFactory, with connection pooling (max 5, min idle 0).
  - Memorystore Redis (host from env, port 6379, timeouts).
  - Flyway enabled with `ddl-auto: validate`.
  - Graceful shutdown (60s). Forward headers strategy.
  - Health probes enabled. Health details visible when authorized.
  - JWT keys injected from environment.
  - Jobs enabled/disabled via `JOBS_ENABLED` env var.
  - Logging: INFO level for `it.mazzoni.vis`.
  - Server port from `PORT` env var (Cloud Run standard).

### Deployment (idempotent deploy script)

- `deploy/k1/k1-deploy.ps1`:
  - Derives image tag from current Git commit (short SHA, 12 chars).
  - Builds the root `Dockerfile` with Cloud Build.
  - Publishes immutable image to Artifact Registry.
  - Deploys Cloud Run service `value-investing-support-k1`:
    - K1 profile, gen2 execution, 1 vCPU, 1 GiB memory, port 8080.
    - Min instances 1, max instances 1, no CPU throttling.
    - Startup probe: `/actuator/health` (initial delay 10s, period 10s, failure threshold 12).
    - Liveness probe: `/actuator/health/liveness` (initial delay 30s, period 30s, failure threshold 3).
    - Concurrency 20, timeout 300s.
    - VPC connector, private-ranges-only egress.
    - Runtime service account from bootstrap output.
    - Secrets injected from Secret Manager (each pinned to version `1`).
    - Environment variables for Cloud SQL connection, database name/username, Redis host/port, market data source, jobs enabled, and alert email disabled.
  - Enforces `--max-instances 1` at both service and revision level.
  - Access mode: `--allow-unauthenticated` (application-level auth protects `/api/**`).
  - Checks that all required secrets have enabled version `1` before deploying.

### Secrets management

- All credentials live exclusively in Secret Manager (cloud) or `.env` (local) — never in images, source control, Terraform state, or logs.
- Deploy script pins every secret to version `1`. Rotation creates a new version and a deliberate Cloud Run revision.
- Secrets: database password, FMP API key, JWT private key, JWT public key, SMTP password.

### IAM and access control

- Runtime service account has minimum permissions: Cloud SQL client, Metric/Log Writer, Secret Manager secret accessor.
- Cloud Run service allows unauthenticated HTTP access. Application authentication (JWT, Google OIDC) protects all `/api/**` routes. `/actuator/health` is publicly reachable for uptime checks.
- Only approved application accounts may be provisioned.

### Monitoring and alerting

- HTTPS uptime check on `/actuator/health`.
- Alert policies for:
  - Uptime-check failure (two consecutive checks).
  - Cloud Run 5xx responses.
  - Container startup failures.
  - Cloud SQL connection exhaustion or unavailability.
  - Memorystore unavailability.
- Structured Cloud Logging. Log levels: INFO for application, ERROR for failures.
- Health endpoint response must not expose component details to anonymous callers.

### Operational documentation

- `deploy/k1/runbook.md` covering:
  - Boundary (K1 is internal/stakeholder-only, not production).
  - Prerequisites (PowerShell 7, Docker, gcloud, authenticated operator).
  - Bootstrap procedure with manual Cloud SQL user creation.
  - Secret loading via `--data-file`.
  - Build and deploy procedure.
  - Monitoring setup (uptime check, alert policies).
  - Validation procedure.
  - Troubleshooting guide with classified failure types.
  - Rollback procedure.
  - K2 handoff notes.

### Validation and smoke testing

- `deploy/k1/k1-validate.ps1`:
  - Verifies Cloud Run service exists, revision is active, max instances is 1.
  - Health check against deployed service (200 OK, UP status).
  - Verifies Flyway ran successfully by querying schema history.
  - Runs a seeded login and read-only research workflow.
  - Confirms structured logs are populated.
- `deploy/k1/k1-rollback.ps1`:
  - Routes all traffic to a specified known-good revision.
  - Validates that rollback succeeds and returns revision details.

### Documentation deliverables

- `deploy/k1/README.md`: brief summary, prerequisites, quick-start commands.

## Decisions

1. **One service, one instance.** K1 tolerates in-process `@Scheduled` jobs by pinning min=max=1 instances with CPU always allocated. K2 will split jobs into Cloud Run Jobs + Cloud Scheduler.
2. **PowerShell 7 for all operational scripts.** Operator runs them from a workstation; no CI/CD pipeline in K1.
3. **`application` access mode (allow-unauthenticated).** Application JWT/OIDC protects data routes. Health endpoint is public for uptime monitoring. Production may use `cloud-run-iam` with a proxy.
4. **Secret Manager with pinned version `1`.** Secrets are never `:latest` to prevent silent config changes. Rotation creates new versions and deliberate revisions.
5. **Cloud Build for the image.** Simple, no local Docker daemon required on the operator's machine. K2 may move to a CI/CD builder.
6. **Cloud SQL user created manually.** `gcloud sql users create` exposes the password on the command line. Manual creation through the Console keeps the password out of shell history and script arguments.
7. **`db-f1-micro` shared-core instance.** Non-production, stakeholder-only. K2 upgrades to a dedicated-core tier with backups and PITR.
8. **Single `$ProjectId` and `$Region` per deployment.** All resources (Cloud SQL, Memorystore, VPC connector, Cloud Run) must be in the same region.
9. **No Terraform in K1.** Scripts are the infrastructure provisioning mechanism. Terraform starts in K2.
10. **No custom domain, no CI/CD, no Cloud Run Jobs, no private connectivity beyond VPC connector.** These belong to K2.

## Out of Scope

- Terraform infrastructure-as-code (K2).
- CI/CD pipeline (K2).
- Cloud Run Jobs + Cloud Scheduler for background work (K2).
- Production private networking, backups, PITR, restore drills (K2).
- Custom HTTPS domain (K2).
- Horizontal scaling beyond one instance (K2).
- Customer-facing release evidence, GDPR mapping, penetration testing (K3).
- FMP data-display-rights confirmation (K3).
- Any change to application business logic, endpoints, or UI behavior. K1 is a deployment-only phase.

## Compatibility and Risks

- **Single-instance coupling:** If the Cloud Run platform replaces or restarts the instance, in-process schedules may miss a cycle or overlap briefly during a cold start with a background task still running. Max-instances=1 and the prohibition on external triggers for the same cron window mitigate duplication risk, but K2 must remove this coupling.
- **Cost:** Always-allocated CPU and min-instances=1 incur a fixed monthly cost. The budget owner must accept this before deployment.
- **Database password handling:** The manual user-creation step is a procedural risk. The runbook must be explicit about clearing clipboard and discarding generated values.
- **Flyway and rollback:** A container rollback does not reverse Flyway migrations. If a K1 deploy includes a migration that is not backward-compatible with the previous revision, the runbook must document a forward-fix/recovery decision.
- **Secret version pinning:** All secrets are pinned to version `1`. If a secret value changes before rotation tooling is built, the operator must manually update the version reference in the deploy script.
- **VPC connector minimum instances:** The connector uses min 2 instances, which incurs cost. Justified by the need to reach both Cloud SQL and Memorystore on private IPs.
- **Yahoo Finance fallback:** The existing fallback behavior is unchanged. FMP remains the primary data source in k1 profile; Yahoo is the runtime fallback as in every other profile.
