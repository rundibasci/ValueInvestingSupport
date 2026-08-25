# K1 — Stakeholder Cloud Deployment

## Context

K1 makes the current Value Investing Support application available to internal stakeholders on Google Cloud without changing its decision-support behaviour. It depends on the completed local platform, including the Spring Boot production profile, Flyway migrations, PostgreSQL and Redis integrations, structured application logging, authenticated demo flows, and operational health indicators.

The deployment is explicitly non-production and internal/stakeholder-only. The API continues to run the existing in-process scheduled jobs temporarily, so Cloud Run must remain limited to one application instance until K2 moves background work to Cloud Run Jobs and Cloud Scheduler.

## Scope

### Container and Runtime Contract

- Review and harden the existing `backend/Dockerfile` as a production Cloud Run image built from a reproducible application artifact.
- Run the Spring Boot service with `SPRING_PROFILES_ACTIVE=prod`, the Cloud Run-provided `PORT`, bounded JVM/container memory settings, and graceful shutdown behaviour.
- Serve the existing static stakeholder/demo pages from the same Cloud Run service.
- Add a build and local smoke workflow that proves the image starts without embedding credentials.
- Keep a single deployable backend service in K1; frontend separation belongs to a later phase unless required to make the existing static pages usable.

### GCP Resources and Configuration

- Document and automate repeatable `gcloud` provisioning for one named non-production environment: required APIs, Artifact Registry, Cloud SQL for PostgreSQL, Memorystore for Redis, Secret Manager, service accounts, IAM bindings, Cloud Run, Cloud Logging, Cloud Monitoring, and an uptime check/alert policy.
- Use explicit configuration variables for project ID, region, environment/resource prefix, image tag or digest, database instance/database/user, Redis instance, Cloud Run service, and notification channel.
- Prefer a region supported by all selected managed services and record the chosen region. K1 does not establish the final commercial data-residency decision.
- Use conservative, low-cost non-production sizing and document expected recurring resources, cost-estimation assumptions, and shutdown/deletion instructions.
- Provisioning scripts must be rerunnable where practical and must detect/report pre-existing resources rather than silently replacing them.

### Data and Connectivity

- Provision a non-production Cloud SQL PostgreSQL instance and application database/user.
- Connect Cloud Run to Cloud SQL using the supported Cloud Run integration and a least-privilege runtime service account.
- Provision Memorystore for Redis and the required VPC/serverless connectivity so Cloud Run can reach Redis without exposing it publicly.
- Let Flyway run on deployment before serving healthy traffic; a migration failure must keep the revision unhealthy and prevent traffic promotion.
- Verify `GET /actuator/health` reports the managed database and Redis connections as healthy without exposing credentials or sensitive topology.

### Secrets and IAM

- Store FMP, JWT private/public key material, database credentials, and enabled SMTP credentials in Secret Manager. SMTP may remain disabled when no delivery account is configured, but the configuration path must be documented.
- Inject secret versions into Cloud Run at runtime. Secret values must never appear in source control, container layers, shell tracing, command output captured in repository artifacts, deployment manifests, logs, or Terraform state.
- Use separate deployer and runtime identities where feasible. Grant the runtime identity only Cloud SQL connection, required Secret Manager access, logging/metrics emission, and other permissions directly needed by the application.
- Do not grant project-wide Owner or Editor roles as part of the documented deployment path.

### Deployment, Observability, and Rollback

- Deploy Cloud Run over its managed HTTPS URL with authenticated application endpoints retaining their existing JWT rules; only intentionally public routes such as `GET /actuator/health` and login remain public at the application layer.
- Configure Cloud Run ingress and invoker access for the agreed stakeholder access model, recording any temporary public-invoker decision and its compensating controls.
- Force `max-instances=1` for K1 so in-process scheduled work is not duplicated. Record this as a temporary architectural constraint and verify it after deployment.
- Emit structured application logs with request correlation and sanitized exception handling into Cloud Logging.
- Configure a basic HTTPS uptime check and an alert route for unhealthy/unreachable service state.
- Provide documented deploy, verification, rollback-to-previous-revision, and full environment cleanup procedures.
- Preserve immutable image tags/digests and deployment revision evidence so rollback does not require rebuilding.

### Functional Smoke Validation

- Verify the managed HTTPS health endpoint.
- Log in with a controlled stakeholder account and exercise at least one authenticated read flow and one core analysis/demo flow.
- Verify database persistence across a Cloud Run revision restart and Redis connectivity/cache behaviour.
- Verify FMP configuration/fallback provenance without recording raw provider responses or credentials.
- Perform and record an actual rollback to the prior known-good Cloud Run revision, then restore the intended revision.

## Decisions

1. **K1 includes an actual GCP deployment.** Repository preparation alone does not complete the phase.
2. **`gcloud` is the K1 provisioning mechanism.** Scripts and runbooks provide repeatability; Terraform becomes the source of truth in K2.
3. **One Cloud Run instance is a hard K1 limit.** Existing `@Scheduled` work remains in-process temporarily, so horizontal scaling is unsafe until K2.
4. **Managed state remains external.** PostgreSQL uses Cloud SQL and Redis uses Memorystore; the Cloud Run filesystem is never treated as durable state.
5. **Flyway runs with the application deployment.** Traffic promotion depends on successful startup and health checks; a separate migration job is deferred to K2 unless deployment testing proves it necessary for safe K1 rollout.
6. **Secrets are referenced, never provisioned as plaintext by repository scripts.** Operators create secret versions through non-logging input paths outside committed configuration.
7. **Internal-only is a release classification, not an excuse to bypass controls.** JWT authorization, least privilege, HTTPS, secret isolation, log sanitation, and rollback evidence are mandatory.
8. **Real rollback is part of acceptance.** A runbook without an executed revision rollback is insufficient for the merge gate.
9. **Cost safety is operational scope.** Resource sizing, current-resource inventory, and cleanup commands are documented; destructive cleanup is executed only with explicit user authorization.
10. **Existing domain outputs remain unchanged.** Valuation, score, recommendation, provenance, freshness, and MiFID II disclaimer behaviour must not change for cloud deployment.

## Out of Scope

- Terraform-managed infrastructure, multiple environments, and infrastructure plan/apply pipelines (K2).
- Moving scheduled work to Cloud Run Jobs or triggering it with Cloud Scheduler (K2).
- Horizontal API scaling beyond one instance while in-process schedules are enabled (K2).
- Custom domain and production DNS, private-only end-user ingress, production HA sizing, PITR exercises, and formal disaster recovery (K2/K3).
- Customer-facing/commercial release, FMP redistribution approval, GDPR release evidence, penetration testing, edge/WAF protection, and compliance certification (K3).
- Changes to valuation formulas, Value Score, recommendation semantics, portfolios, watchlists, or the AI thesis roadmap.
- Storing or copying real secret values into repository documentation, scripts, test fixtures, screenshots, or Obsidian notes.

## Compatibility and Risks

- Cloud Run terminates TLS and supplies `PORT`; hard-coded port or proxy-header assumptions can prevent startup or produce incorrect redirects.
- Cloud SQL and Memorystore connectivity uses Direct VPC egress and private service routing. Network configuration can fail independently of the application, but K1 avoids a continuously billed Serverless VPC Access connector.
- A single instance avoids duplicate schedules but creates planned stakeholder-demo downtime during some failures and deployments; this is accepted for K1 only.
- Flyway-at-startup can lengthen readiness and makes incompatible migrations especially risky. Migrations must remain backward-compatible with the previous revision to support rollback.
- The public Actuator health response must not leak component details, resource identifiers, credentials, or provider payloads.
- Cloud Run CPU allocation and instance idling can make in-process schedules unreliable. K1 accepts scheduled jobs only for controlled stakeholder use and requires explicit operational verification; K2 removes this limitation.
- Enabling unauthenticated Cloud Run invocation may expose application-public routes to the internet even when business endpoints require JWT. The selected ingress/invoker model must be recorded and reviewed before deployment.
- Managed GCP resources incur cost while idle. The handoff note must identify all running billable resources and the exact approved next action.
