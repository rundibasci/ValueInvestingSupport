# K1 Stakeholder Cloud Deployment — Requirements

## Purpose

Deliver roadmap phase **K1 — Stakeholder Cloud Deployment** (milestone M24): make the working Value Investing Support application safely accessible to internal stakeholders on Google Cloud without changing its decision-support behavior.

K1 is deliberately bounded. It proves a production container and a usable managed deployment with basic operations. It does not implement the production-shaped Terraform, Cloud Run Jobs, CI/CD, private networking, recovery, or commercial-compliance scope reserved for K2 and K3.

## Scope

### Cloud Run application

- Build one production container for the Spring Boot service and its served React/static demo assets.
- Deploy the application as one controlled Cloud Run service reachable through an HTTPS URL.
- Honor Cloud Run runtime conventions and expose usable startup and Actuator health signals.
- Pin the service to a maximum of one instance while existing `@Scheduled` work remains in-process, preventing duplicated background execution.
- Preserve existing authenticated API, frontend, demo, and ADMIN workflows.

### Managed PostgreSQL and Redis

- Use non-production Cloud SQL for PostgreSQL as the persistent system of record.
- Use Memorystore for Redis for market-data/computation caches and Redis-backed token state.
- Apply Flyway migrations through a controlled, observable deployment step.
- Verify `/actuator/health` and representative workflows against both managed dependencies.
- Preserve immutable historical snapshots and cache-first FMP/Yahoo fallback behavior.

### Secrets and access

- Store FMP, JWT, SMTP, and any other confidential runtime configuration in Secret Manager.
- Inject secrets at runtime; never embed values in images, source control, logs, evidence, or documentation.
- Use a dedicated least-privilege runtime service account.
- Restrict the environment to approved internal/stakeholder users.
- Keep public/commercial launch explicitly out of scope.

### Basic observability and operations

- Send sanitized structured logs to Cloud Logging.
- Add basic uptime/health monitoring and actionable notification ownership.
- Document manual image publication, deployment, health verification, troubleshooting, and rollback.
- Use immutable image/revision identifiers so the deployed version is auditable.
- Record K1 cost, scaling, scheduling, networking, backup, and operational limitations for K2.

## Decisions

1. K1 uses Cloud Run, Cloud SQL for PostgreSQL, Memorystore for Redis, Secret Manager, Artifact Registry, and Cloud Logging/Monitoring.
2. One controlled Cloud Run instance may temporarily retain existing in-process scheduled jobs.
3. Horizontal scaling is prohibited in K1 until K2 separates background work into Cloud Run Jobs triggered by Cloud Scheduler.
4. K1 may use documented manual provisioning/deployment; Terraform and CI/CD become required in K2.
5. K1 is an internal evaluation environment, not a production or customer-facing release.
6. Application domain behavior, valuation logic, score logic, data ownership, availability semantics, and disclaimers remain unchanged.

## Context and Guardrails

- The API remains stateless; PostgreSQL and Redis retain durable and cache/token state.
- Recommendations remain transparent decision support and retain all MiFID II disclaimers.
- External data remains cache-first with automatic Yahoo fallback when FMP is unavailable.
- Historical fundamentals remain immutable.
- Missing data remains explicit and is never fabricated during deployment or provider failure.
- Provider DTOs, secrets, authentication material, and raw payloads must not enter logs or public responses.
- The Java 21/Spring Boot, PostgreSQL 16, Redis 7, React/TypeScript, Flyway, Actuator, and structured logging choices remain authoritative.

## Assumptions

- The GCP organization/project, billing account, deployment region, stakeholder identities, and operator will be supplied before implementation reaches live provisioning.
- Manual QA is possible when the implementer has appropriate GCP and application credentials.
- A single Cloud Run instance provides sufficient K1 capacity for stakeholder evaluation.
- The selected managed-service connectivity path can be tightened or replaced during K2 without changing application contracts.

## Dependencies

- A GCP project with billing and APIs enabled.
- Permissions to use Cloud Run, Artifact Registry, Cloud SQL, Memorystore, Secret Manager, IAM, Logging, and Monitoring.
- Approved non-production secret values and stakeholder identities.
- A successfully built and tested application revision.
- Operational ownership for alerts, deployments, and rollback.

## Out of Scope

- Terraform-managed environments and fully automated CI/CD.
- Cloud Run Jobs, Cloud Scheduler, or horizontal API scaling.
- Production-grade private networking, custom DNS/domain, edge protection, and rate limiting.
- Formal backup/PITR and restore drills beyond documenting the K1 limitation.
- Multi-environment promotion and zero-downtime migration automation.
- Commercial release, FMP redistribution approval, GDPR/compliance completion, or K3 security evidence.
- Changes to financial calculations, research workflows, portfolio behavior, or application roles.
- Kubernetes or a new application architecture.
