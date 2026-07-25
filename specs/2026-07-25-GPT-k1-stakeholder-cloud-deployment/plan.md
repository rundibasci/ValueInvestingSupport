# K1 — Implementation Plan

## 1. Containerization and Spring Boot K1 Profile

1. Write root `Dockerfile`: multi-stage Maven build (Java 21) → slim JRE runtime, expose 8080, Actuator health probes.
2. Write `application-k1.yml`: Cloud SQL SocketFactory datasource, Memorystore Redis config, Flyway enabled, graceful shutdown, health probes, JWT env injection, jobs toggle, logging config.
3. Verify the Docker image builds locally and the backend starts against a local PostgreSQL/Redis for smoke testing before Cloud Run.
4. Add `.dockerignore` if not already present.

## 2. GCP Bootstrap Script (Infrastructure Provisioning)

1. Write `deploy/k1/k1-bootstrap.ps1` (PowerShell 7, `SupportsShouldProcess`):
   - Enable required APIs.
   - Create Artifact Registry repository.
   - Create runtime service account + IAM bindings.
   - Create Cloud SQL PostgreSQL instance and `vis` database.
   - Report manual database user creation step (warning, not error).
   - Create Serverless VPC Access connector.
   - Create Memorystore Redis instance.
   - Create empty Secret Manager secrets + grant runtime SA access.
   - Print connection name, Redis host, and next-step instructions.
2. Make every `gcloud` resource creation conditional on the resource not already existing.
3. Use `--data-file` pattern for secret loading guidance in the runbook (bootstrap never handles secret values).

## 3. Deployment Script

1. Write `deploy/k1/k1-deploy.ps1`:
   - Accept mandatory `ProjectId`, `Region`, `CloudSqlInstance`, `RedisHost`.
   - Derive image tag from `git rev-parse --short=12 HEAD`.
   - Validate all required secrets have enabled version `1`.
   - Build with `gcloud builds submit --tag`.
   - Deploy to Cloud Run with the K1 configuration (1 instance, always-allocated CPU, VPC connector, secret bindings, env vars).
   - Enforce `--max-instances 1` post-deploy.
   - Print service URL, revision, image, and access mode.
2. Support `application` (default) and `cloud-run-iam` access modes.

## 4. Rollback Script

1. Write `deploy/k1/k1-rollback.ps1`:
   - Accept `ProjectId`, `Region`, `Revision`.
   - Route 100% traffic to specified revision.
   - Validate the revision exists before routing.
   - Print new traffic configuration for confirmation.

## 5. Validation Script

1. Write `deploy/k1/k1-validate.ps1`:
   - Verify Cloud Run service exists, revision is active, max-instances is 1.
   - Call `/actuator/health` on the deployed service — expect 200, UP status.
   - Run a read-only research workflow: authenticate, seed a small ticker list if needed, run quick-analysis, verify response structure.
   - Check structured logs are present in Cloud Logging.
   - Support identity-token mode for `cloud-run-iam` access.
2. Document manual QA sections that require a browser (login, screener, security detail, portfolio).

## 6. Operational Runbook

1. Write `deploy/k1/runbook.md`:
   - Boundary statement: K1 is internal/stakeholder-only.
   - Prerequisites: PowerShell 7, Docker, gcloud CLI, authenticated operator, selected project/region, accepted budget.
   - Bootstrap procedure with the manual Cloud SQL user creation step.
   - Secret loading: `gcloud secrets versions add --data-file`.
   - Build and deploy procedure with example commands.
   - Monitoring setup: uptime check, alert policies.
   - Validation: script + manual QA.
   - Troubleshooting: classified failure types, log inspection commands, Flyway query without credentials.
   - Rollback: list revisions, route to known-good.
   - K2 handoff: what must change before horizontal scaling and production use.
2. Write `deploy/k1/README.md`: succinct summary, prerequisites, quick-start commands.

## 7. Monitoring and Alerting Configuration

1. Document the procedure for creating an HTTPS uptime check on `/actuator/health`.
2. Document alert policy creation for the five required conditions (uptime failure, 5xx, startup failure, Cloud SQL exhaustion, Memorystore unavailability).
3. Record alert-policy names and notification-channel owners in the validation report (never commit personal addresses or webhook credentials).

## 8. Verification and Merge Readiness

1. Build the Docker image and verify the backend starts successfully in the container.
2. Backend: `./mvnw test` — all existing tests pass; no new regressions.
3. Frontend: `npm run typecheck && npm run build` — passes.
4. Git hygiene: `git diff --check`, no unrelated changes.
5. If GCP access is available: run bootstrap → deploy → validate → rollback → re-deploy cycle on a real project. Capture sanitized evidence (revision, image digest, health, Flyway version, alert delivery, max-instances config, known limitations).
6. Complete `validation.md` acceptance criteria.
7. Review the diff for scope compliance, authorization safety, and secret safety before merge.
