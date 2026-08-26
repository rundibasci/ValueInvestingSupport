# K2 — Implementation Plan

## 1. Terraform Foundations and State

1. Create `terraform/modules/{network,iam,secret-manager,cloud-sql,redis,cloud-run-service,cloud-run-job,cloud-scheduler,monitoring}/` and `terraform/environments/{dev,staging}/`, each environment directory holding `main.tf`, `variables.tf`, `terraform.tfvars`, and `backend.tf`.
2. Provision, once and outside Terraform, a GCS bucket (e.g. `vis-terraform-state`) with object versioning enabled; point each environment's `backend.tf` at a distinct state object prefix (`dev/terraform.tfstate`, `staging/terraform.tfstate`).
3. Define shared variables (`project_id`, `region`, `environment`, `resource_prefix`) and adopt the `vis-k2-{env}-{resource}` naming convention, distinct from K1's retired `vis-k1-*` prefix.
4. Add `terraform fmt -check` and `terraform validate` to the local/documented pre-apply workflow for both environments.
5. Document the one-time bootstrap steps (state bucket creation, required API enablement, Workload Identity Federation pool/provider setup) that must happen before any environment's first `terraform apply`.

## 2. Networking, IAM and Secret Manager (Terraform)

1. `module "network"`: VPC, subnet, private service access range, and Direct VPC egress configuration per environment, mirroring K1's proven topology.
2. `module "secret_manager"`: Secret Manager secret containers (never values) for FMP, JWT, database, and SMTP credentials per environment; module outputs secret resource IDs for IAM binding by other modules.
3. `module "iam"`: a deployer service account (used by CI/CD, scoped to Terraform-managed resource types) and a runtime service account (Cloud SQL client, per-secret Secret Manager accessor, logging/metrics writer) per environment — no project-wide Owner/Editor role anywhere.
4. Configure Workload Identity Federation between the GitHub repository/environment and each environment's deployer service account; no service-account JSON key is generated or stored.
5. Run `terraform plan` and manually verify no IAM binding exceeds the documented least-privilege set before first apply.

## 3. Cloud SQL and Redis — Private Connectivity, Backups, PITR

1. `module "cloud_sql"`: Cloud SQL PostgreSQL instance per environment, private IP only, dev-tier sizing (matching K1's `db-f1-micro`/10 GB baseline), automated backups enabled, point-in-time recovery enabled with an explicit retention window variable.
2. `module "redis"`: Memorystore Redis Basic tier per environment, direct peering, matching K1's 1 GB sizing.
3. Export the PITR-eligible retention window and backup schedule as module outputs consumed by the runbook and the Group 9 restore drill.
4. Confirm Flyway still runs at application startup against the Terraform-provisioned instance; no migration logic moves into Terraform.
5. Document the exact `gcloud sql instances clone` / point-in-time restore command sequence to be executed (against `staging` only) in Group 9.

## 4. Cloud Run API Service (Terraform-managed, multi-environment)

1. `module "cloud_run_service"`: Cloud Run v2 service resource parameterized by environment, image tag/digest, resource limits (`cpu`, `memory`), and `max_instances`.
2. Set `max_instances = 1` for both environments initially, keeping `DeploymentProperties`/`K1DeploymentGuard` active until Group 5's job migration is verified in that environment.
3. Bind Cloud SQL connectivity and Secret Manager references via Terraform, reusing K1's runtime-identity pattern but now environment-scoped through the `module "iam"` outputs.
4. Add a Cloud Run domain-mapping resource for `staging` only, targeting the chosen custom HTTPS subdomain; `dev` keeps its default `run.app` URL.
5. Run `terraform plan` before the first apply in each environment and confirm every planned action is additive — no resource-name collision with any residual K1-era resource.

## 5. Background Work Migration — Cloud Run Jobs and Cloud Scheduler

1. Add a `SCHEDULING_MODE` application property (`in-process` | `job`) read by a new `K2SchedulingGuard` (parallel to `K1DeploymentGuard`) that disables `@Scheduled` execution when `SCHEDULING_MODE=job`.
2. Extract each existing scheduled task — ingestion, quote refresh, dividend/insider updates, alert detection — into a Spring Boot CLI-invocable entry point (e.g. `--job=ingestion`) that Cloud Run Jobs can execute per run without starting the full web server.
3. `module "cloud_run_job"`: one Cloud Run Job resource per task, reusing the K2 application image with a job-specific command/argument, one instance per environment.
4. `module "cloud_scheduler"`: one Cloud Scheduler HTTP trigger per Job, authenticated via OIDC against the Cloud Run Jobs execution API, preserving existing cadence (nightly bulk sync, 15-minute quote refresh, etc.).
5. Extend `JobRunLog` so a Cloud Run Jobs execution is recorded with the same fields as the current in-process run, including retry attempts and exit status.
6. Add `K2SchedulingGuardTest` verifying that no `@Scheduled` method executes when `SCHEDULING_MODE=job`; only after this test and a live verification pass does Group 4's `max_instances` raise beyond 1 for staging.

## 6. CI/CD Pipeline (GitHub Actions)

1. Add `.github/workflows/backend-ci.yml`: on every pull request, run `cd backend && ./mvnw test` and `terraform fmt -check`/`terraform validate` for both environment directories.
2. Add `.github/workflows/k2-deploy-dev.yml`: on merge to `main`, build and publish an immutable Artifact Registry image tagged by commit SHA, run `terraform plan`+`apply` against `environments/dev`, then run Flyway/health smoke checks against the resulting revision.
3. Add `.github/workflows/k2-deploy-staging.yml`: `workflow_dispatch`-gated promotion of the exact image digest already deployed to `dev` into `environments/staging` — never rebuilds from source for staging.
4. Configure GitHub Actions Environments `dev` and `staging`, each holding its own Workload Identity Federation binding; no credential is shared between them.
5. Add a documented rollback workflow step (manual dispatch): redeploy the previous immutable image digest, or re-apply the previous Terraform state, without a source rebuild.

## 7. Monitoring, Alerting and Custom Domain

1. `module "monitoring"`: uptime checks and alert policies (error rate, latency, instance count, Cloud SQL/Redis health) per environment, wired to the notification-channel pattern established in K1.
2. Add Cloud Monitoring dashboards for API latency/error rate, Cloud Run Jobs success/failure rate, and Cloud SQL/Redis utilization, per environment.
3. Provision the `staging` custom HTTPS domain's managed SSL certificate and verify DNS delegation.
4. Verify structured logs retain request correlation across both the Cloud Run service and the new Cloud Run Jobs.
5. Document Cloud Monitoring queries specific to Jobs/Scheduler failures, extending K1's existing query documentation in the runbook.

## 8. Automated Verification

1. Add focused backend tests: `K2SchedulingGuardTest`, per-job CLI entry-point tests, and `SCHEDULING_MODE` configuration-binding tests.
2. Run the complete backend suite (`cd backend && ./mvnw test`) and `terraform validate`/`terraform plan` for both environments with zero unexpected diff.
3. Run static checks (`bash -n` on any new scripts, `git diff --check`) across all new Terraform, workflow, and application files.
4. Lint GitHub Actions workflow YAML (`actionlint` if available; otherwise a manual review focused on secret handling and least-privilege `permissions:` blocks).
5. Confirm no secret value appears in `terraform plan` output, CI logs, or any committed `.tfvars` file.

## 9. Live GCP Acceptance, Restore Drill, and Handoff

1. Apply `environments/dev` and `environments/staging` from a clean Terraform state; verify each reaches a healthy Cloud Run revision independently.
2. Execute the full CI/CD pipeline end to end at least once: PR checks → merge → automatic `dev` deploy → manually gated `staging` promotion of the same image digest.
3. Trigger each Cloud Run Job manually once per environment, confirm `JobRunLog` records success, then confirm the corresponding Cloud Scheduler trigger fires it automatically on its configured schedule.
4. Execute a real Cloud SQL point-in-time-recovery restore drill against the `staging` instance only: capture a known state, force a recoverable change, restore to the pre-change point in time, and verify the restored data matches the captured state.
5. Update `validation.md` with exact commands, environment URLs, revisions, timestamps, sanitized evidence, and any remaining gaps.
6. Update the K2 Obsidian handoff note with branch/commit/push state, running resources per environment, measured or estimated costs, and the exact next action.
7. Mark K2 complete and update the roadmap only after every merge-gate condition passes for both `dev` and `staging`.
