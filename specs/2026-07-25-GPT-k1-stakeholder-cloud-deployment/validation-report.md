# K1 Stakeholder Cloud Deployment — Validation Report

Date: 2026-07-25
Branch: `phase/k1-stakeholder-cloud-deployment`

## Implemented

- Full-stack, multi-stage root container build:
  - React production build with same-origin API configuration.
  - Java 21 Spring Boot build containing React assets and existing static demo pages.
  - Non-root runtime user and bounded JVM memory behavior.
  - Build context excludes secrets, local output, tests, specs, and logs.
- Dedicated `k1` Spring profile:
  - Cloud Run `PORT`.
  - Cloud SQL Java Connector with lazy refresh and bounded Hikari pool.
  - Memorystore Redis configuration.
  - Flyway enabled and JPA validation.
  - graceful shutdown and Actuator probes.
  - structured JSON logging.
- React SPA route forwarding from the Spring Boot service.
- Manual K1 GCP automation:
  - API enablement and Artifact Registry.
  - least-privilege runtime service account.
  - non-production Cloud SQL PostgreSQL 16 database.
  - Redis 7 Memorystore and Serverless VPC Access connector.
  - empty Secret Manager resources with runtime access grants.
  - immutable image build and Cloud Run deployment.
  - pinned secret versions.
  - one warm, CPU-allocated instance and both revision/service maximums of one.
  - HTTP startup/liveness probes.
  - application-auth or Cloud Run IAM access modes.
  - configuration/smoke validator and revision rollback.
- Operator runbook covering secrets, deployment, monitoring, validation, troubleshooting, rollback, migration safety, and K2 handoff.

## Local Evidence

| Check | Result |
|---|---|
| PowerShell parser for all K1 scripts | PASS |
| Focused `SpaWebConfigTest` | PASS — 2 tests |
| Complete backend `mvnw test` | PASS — 455 tests, 0 failures/errors |
| Frontend `npm run build` | PASS |
| `git diff --check` | PASS |
| Secret-pattern review of changed deployment/config files | PASS — placeholders and Secret Manager references only |

The frontend build retains an existing Vite advisory that the main JavaScript chunk exceeds 500 kB. It does not fail the build and is not introduced by K1 application behavior.

## Environment-Limited Checks

- Local `docker build -t vis-k1:local .` could not run because the Docker Desktop Linux engine was not running.
- Live GCP bootstrap, Cloud Build, Cloud Run deployment, Cloud SQL/Memorystore health, Flyway-on-managed-PostgreSQL, alerts, stakeholder walkthrough, and rollback could not run because `gcloud` is not installed/configured in this environment and no GCP project/credentials were supplied.

These checks remain mandatory before K1 is considered deployed or merge-ready. Follow `deploy/k1/runbook.md` and complete the manual QA checklist in `validation.md`.

## Merge Assessment

The repository implementation is ready for an operator-backed K1 deployment trial. K1 itself is not yet operationally complete: merge readiness still requires a successful immutable image build plus live manual QA and rollback evidence from the selected GCP project.
