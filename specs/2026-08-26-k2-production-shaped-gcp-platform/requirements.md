# K2 — Production-Shaped GCP Platform

## Context

K2 makes the GCP deployment K1 proved out repeatable, environment-separated, and operationally safe. K1 validated that the application runs on Cloud Run against managed PostgreSQL and Redis, but relied on ad hoc `gcloud` scripts, a single manually provisioned environment, in-process `@Scheduled` jobs capped at one Cloud Run instance (`K1DeploymentGuard`), and no CI/CD pipeline. K1's live GCP resources were fully torn down after stakeholder validation (see `specs/2026-08-25-k1-stakeholder-cloud-deployment/validation.md` → Closure Disposition), so K2 provisions from zero rather than importing existing infrastructure.

K2 depends on K1's container image contract, Spring Boot production profile, `DeploymentProperties`/`SpaForwardController`, and the closed K1 runbook (`docs/operations/k1-gcp-runbook.md`) as its starting reference, but supersedes K1's `gcloud`-script provisioning mechanism with Terraform as the source of truth, per `specs/tech-stack.md` → Infrastructure as Code and K1's own Decision 2 ("Terraform becomes the source of truth in K2").

## Scope

### Terraform Foundations and Multi-Environment Topology

- Introduce `terraform/modules/*` (network, iam, secret-manager, cloud-sql, redis, cloud-run-service, cloud-run-job, cloud-scheduler, monitoring) and `terraform/environments/{dev,staging}/`, each with its own state, `.tfvars`, VPC, Cloud SQL instance, Redis instance, Cloud Run service, service accounts, and secrets.
- No environment shares mutable infrastructure with another; naming uses a `vis-k2-{env}-*` prefix distinct from K1's retired `vis-k1-*` prefix.
- Terraform state lives in a dedicated GCS bucket with versioning enabled, one state object path per environment.

### Networking, IAM and Secret Management

- Reuse K1's proven private-connectivity topology (VPC, subnet, private service access, Direct VPC egress) per environment, now Terraform-managed.
- Two service accounts per environment: a deployer identity (used by CI/CD, scoped to Terraform-managed resource types) and a runtime identity (least privilege: Cloud SQL client, per-secret Secret Manager accessor, logging/metrics writer).
- GitHub Actions authenticates to GCP via Workload Identity Federation; no long-lived service-account JSON key is stored in GitHub or on disk.
- Terraform manages Secret Manager secret **containers** and IAM bindings only; secret **values** are created and rotated outside Terraform state, through the same non-logging, non-committed mechanism K1 established.

### Cloud SQL and Redis — Private Connectivity, Backups, PITR

- Cloud SQL PostgreSQL per environment, private IP only, conservative dev-tier sizing, automated backups enabled, point-in-time recovery enabled with a documented retention window.
- Memorystore Redis Basic tier per environment, direct peering, matching K1's proven sizing.
- Flyway continues to run at application startup; Terraform never takes on schema-management responsibility.

### Background Work Migration

- Move ingestion, quote refresh, dividend/insider updates, and alert detection from in-process `@Scheduled` methods to one Cloud Run Job per task, each triggered by its own Cloud Scheduler entry over OIDC.
- Preserve existing job cadence, idempotency, and `JobRunLog` observability; a job run through Cloud Run Jobs must be indistinguishable in outcome from the current in-process run.
- Retire the `K1DeploymentGuard` single-instance constraint only once every scheduled task runs exclusively through Cloud Run Jobs.

### Cloud Run API Service

- Cloud Run v2 service per environment, Terraform-managed, parameterized by image tag/digest and per-environment resource limits.
- `max-instances=1` remains in force until Group 5's job migration is verified in that environment; staging may then raise to a conservative `max-instances=3`, dev stays at 1.
- A custom HTTPS domain applies to staging only in K2; dev keeps its default `run.app` URL.

### CI/CD Pipeline

- GitHub Actions runs build/test/`terraform validate` on every pull request; merging to `main` builds and publishes an immutable Artifact Registry image and deploys it to `dev` automatically; promotion of the same image digest to `staging` is a manually gated step that never rebuilds from source.
- GitHub Actions Environments (`dev`, `staging`) hold environment-scoped Workload Identity Federation credentials; no credential is shared across environments.
- Rollback is a pipeline action: re-apply the previous Terraform state or redeploy the previous immutable image digest without rebuilding.

### Monitoring, Alerting and Restore Drill

- Uptime checks, alert policies (error rate, latency, instance count, Cloud SQL/Redis health), and dashboards per environment, extending K1's notification-channel pattern.
- A real Cloud SQL point-in-time-recovery restore drill runs against the staging instance in K2 (pulled forward from K3 at the user's explicit request): capture a known state, force a recoverable change, restore to the pre-change point in time, and verify data matches the captured state.

## Decisions

1. **Terraform is authoritative from K2 onward.** `gcloud` scripts remain as K1 historical reference only; no new provisioning script is added outside Terraform.
2. **Directory-per-environment, not Terraform workspaces.** `terraform/environments/{dev,staging}` each carry their own state and backend configuration, avoiding workspace state-sharing footguns and keeping blast radius per environment explicit.
3. **GitHub Actions is the CI/CD provider** (confirmed by the user). The repository is already hosted on GitHub; Workload Identity Federation removes the need for a stored service-account key.
4. **K2 provisions fresh.** K1's resources were already torn down before K2 began, so no `terraform import` step is required — verify this remains true immediately before the first `apply`.
5. **Cloud Run Jobs boundary matches the roadmap's named background tasks** one-to-one: ingestion, quote refresh, dividend/insider updates, alert detection. No task is split across multiple Jobs, and no unrelated task is folded into an existing one.
6. **The single-instance constraint is lifted only after the job migration is verified**, not before; `K1DeploymentGuard` (or its K2 replacement) must actively fail a deployment that still has `@Scheduled` execution enabled once `SCHEDULING_MODE=job` is set.
7. **Custom HTTPS domain is staging-only in K2.** Dev does not get a custom domain, keeping DNS/certificate surface minimal until commercial release planning in K3 revisits domain strategy.
8. **A PITR restore drill is performed in K2**, using the staging instance only; dev never carries a restore drill that could disrupt ongoing development use.
9. **No new data source or valuation/scoring behaviour is introduced.** K2 is infrastructure-only; FMP/Yahoo fallback semantics and all decision-support outputs are unchanged.
10. **Two environments only.** K2 proves the Terraform/CI/CD pattern with `dev` and `staging`; a third, commercial-production environment is a K3+ decision, not created here.
11. **The first live pass is verify-then-teardown, not a standing deployment.** Group 9's live GCP acceptance (apply, job/scheduler verification, CI/CD pipeline run, PITR restore drill) proves the full Terraform lifecycle including `destroy`, exactly as K1 proved deploy-then-teardown. After acceptance evidence is captured, both environments are torn down. Standing up `dev`/`staging` as continuously running, CI/CD-fed environments is a separate, explicitly authorized "official deploy" action the user triggers later — K2's merge gate is about proving the pattern works repeatably, not about leaving infrastructure running.

## Out of Scope

- Commercial/customer-facing release, GDPR release evidence, penetration testing, and compliance certification (K3).
- Production HA sizing beyond `dev`/`staging` conservative tiers, and any disaster-recovery exercise beyond the K2 PITR drill (K3).
- New product features, valuation/scoring changes, or the AI Investment Thesis integration (Group TA) — those are independent of Group K per `specs/mission.md`.
- A third (production) Terraform environment.
- Edge/WAF protection, edge rate limiting, and automated security/dependency scanning (K3).
- Reconciling or deleting the two stray, unmerged K1 experiment branches (`phase/k1-stakeholder-cloud-deployment`, `feature/deepseek-k1-stakeholder-cloud-deployment`) — untouched, out of scope for K2.

## Compatibility and Risks

- Migrating `@Scheduled` jobs to Cloud Run Jobs changes execution semantics: no shared JVM state between runs, a cold start per execution. Job logic must be verified idempotent and safe to retry before the guard is retired.
- Multi-environment from day one doubles the managed-service footprint (two Cloud SQL + two Redis instances). Both must use conservative, documented dev-tier sizing to keep K2's always-on cost bounded and visible.
- A live PITR restore drill against Cloud SQL is inherently disruptive to the instance it targets; it must run only against the isolated `staging` instance, never against `dev` or a future production instance.
- GitHub Actions deploying to GCP requires correctly scoped Workload Identity Federation; a misconfigured trust policy is either a security exposure (too broad) or a broken pipeline (too narrow) — both must be verified before relying on the pipeline for any deploy.
- Terraform's first `apply` against `vis-k2-*` names must be preceded by a live check that no same-named resource already exists, even though K1's resources are confirmed torn down as of the K1 closure note.
- Existing K1 runtime contracts (`DeploymentProperties`, health endpoint shape, JWT authorization boundary) must remain unchanged for the Cloud Run service; K2 changes how infrastructure is provisioned and how background work executes, not the application's external behaviour.
