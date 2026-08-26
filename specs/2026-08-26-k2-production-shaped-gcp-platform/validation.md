# K2 — Validation

## Acceptance Criteria

- [ ] `terraform apply` provisions `dev` and `staging` independently from a clean state, with no shared mutable infrastructure between them.
- [ ] Terraform state lives in a versioned GCS bucket, one state path per environment; no state is stored locally or in source control.
- [ ] No Terraform-managed IAM binding grants project-wide Owner or Editor in either environment.
- [ ] Secret Manager containers are Terraform-managed; secret values are absent from Terraform state, plan output, `.tfvars`, and CI logs.
- [ ] GitHub Actions authenticates to GCP via Workload Identity Federation; no service-account JSON key exists in the repository or GitHub secrets.
- [ ] Cloud SQL and Redis are private-IP only in both environments, with automated backups and point-in-time recovery enabled.
- [ ] Every scheduled task (ingestion, quote refresh, dividend/insider updates, alert detection) runs exclusively as a Cloud Run Job triggered by Cloud Scheduler; no `@Scheduled` method executes when `SCHEDULING_MODE=job`.
- [ ] `JobRunLog` records a Cloud Run Jobs execution with the same fidelity (success/failure, retry attempts) as the former in-process run.
- [ ] The CI/CD pipeline runs PR checks, deploys `dev` automatically on merge to `main`, and promotes the identical image digest to `staging` only through a manually gated step.
- [ ] A documented rollback pipeline action redeploys the previous image digest or Terraform state without a source rebuild.
- [ ] Uptime checks, alert policies, and dashboards exist per environment and are wired to a real notification channel.
- [ ] `staging` is reachable over its custom HTTPS domain with a valid managed certificate; `dev` remains on its default `run.app` URL.
- [ ] A real Cloud SQL point-in-time-recovery restore drill against `staging` completes successfully and restored data matches the captured pre-drill state.
- [ ] Existing K1 runtime contracts (health endpoint shape, JWT authorization boundary, MiFID II disclaimer behaviour) are unchanged in both environments.
- [ ] After acceptance evidence is captured, `terraform destroy` removes `staging` then `dev` cleanly, and a project-wide sweep confirms zero remaining billable resources — the merge gate does not require `dev`/`staging` to remain running.

## Repository Test Matrix

| Scenario | Expected result |
|---|---|
| `SCHEDULING_MODE=job` at startup | All `@Scheduled` methods are disabled; `K2SchedulingGuardTest` passes |
| `SCHEDULING_MODE=in-process` (default/local) | Existing scheduled-job behaviour is unchanged from pre-K2 |
| Per-job CLI entry point invoked directly | The job runs to completion and writes a `JobRunLog` entry identical in shape to the in-process run |
| Missing/invalid job argument | The process exits non-zero with a clear error, without starting the web server |
| `terraform validate` (dev, staging) | Both configurations are syntactically and internally valid |
| `terraform plan` against a clean state | Every planned resource is additive; no unexpected destroy/replace action |
| `terraform plan` re-run with no changes | Plan is empty (idempotent) |
| GitHub Actions workflow lint | No workflow grants excess `permissions:` scope or echoes a secret |
| Shell/static checks | Any new scripts are syntactically valid and do not echo sensitive values |

## Live GCP Test Matrix

| Scenario | Environment | Expected result |
|---|---|---|
| Initial `terraform apply` | dev, staging | All planned resources are created; Cloud Run revision becomes ready |
| Cloud SQL connection | dev, staging | Flyway completes; database health is `UP` |
| Memorystore connection | dev, staging | Redis health/cache operations succeed without public exposure |
| Manual Cloud Run Job trigger (each of the 4 tasks) | dev, staging | Job completes; `JobRunLog` shows success; downstream data reflects the run |
| Cloud Scheduler automatic trigger | staging | Job fires on its configured cadence without manual intervention |
| CI/CD: PR opened | — | Build/test/`terraform validate` checks run and report status on the PR |
| CI/CD: merge to `main` | dev | Image builds, publishes, and deploys automatically; smoke checks pass |
| CI/CD: manual staging promotion | staging | The exact `dev` image digest deploys to staging without rebuilding |
| CI/CD: rollback dispatch | dev or staging | Previous image digest/Terraform state redeploys successfully |
| Custom domain request | staging | Custom HTTPS domain resolves with a valid managed certificate |
| Uptime failure simulation or policy inspection | dev, staging | Alert policy is correctly wired and evidence is recorded safely |
| Cloud SQL PITR restore drill | staging only | Restore completes; data matches the captured pre-drill state |
| Instance configuration inspection | dev, staging | `max_instances` matches the documented per-environment value |

## Security and Secret Checks

- [ ] `git diff` and tracked-file search contain no credential values, private key bodies, or service-account JSON key material.
- [ ] `terraform plan`/`apply` output contains no secret value — only Secret Manager resource references.
- [ ] Workload Identity Federation trust policy is scoped to this repository/environment only, not organization-wide.
- [ ] Runtime service-account roles match the documented minimum permissions per environment.
- [ ] Cloud SQL and Redis are not exposed through unintended public endpoints in either environment.
- [ ] CI/CD logs contain no JWTs, passwords, API keys, SMTP credentials, or database connection secrets.
- [ ] Cloud Run Jobs logs contain no provider payloads or credential values.

## Regression Checks

- [ ] Existing backend tests pass, or unrelated failures are evidenced precisely.
- [ ] Existing local/demo Docker workflows remain usable unchanged.
- [ ] Authentication and role authorization behave identically to pre-K2.
- [ ] Flyway remains compatible with a clean database and the deployed schema in both environments.
- [ ] Valuation, scoring, recommendation, freshness, provenance, and fallback semantics are unchanged.
- [ ] All fair-value, score, and recommendation outputs retain the MiFID II decision-support disclaimer.
- [ ] Data produced by a Cloud Run Job matches what the equivalent in-process `@Scheduled` run previously produced (same idempotency/dedup behaviour).
- [ ] No user-owned portfolio, watchlist, alert, or account data is exposed across users or across environments.

## Verification Commands

Use the exact scripts and variables established during implementation; record sanitized results under Validation Evidence. At minimum:

```bash
cd backend && ./mvnw test
terraform -chdir=terraform/environments/dev fmt -check
terraform -chdir=terraform/environments/dev validate
terraform -chdir=terraform/environments/dev plan
terraform -chdir=terraform/environments/staging validate
terraform -chdir=terraform/environments/staging plan
gcloud run services describe vis-k2-dev-api --region "$K2_GCP_REGION" --project "$K2_GCP_PROJECT_ID"
gcloud run services describe vis-k2-staging-api --region "$K2_GCP_REGION" --project "$K2_GCP_PROJECT_ID"
gcloud run jobs list --region "$K2_GCP_REGION" --project "$K2_GCP_PROJECT_ID"
gcloud scheduler jobs list --location "$K2_GCP_REGION" --project "$K2_GCP_PROJECT_ID"
gcloud sql instances describe vis-k2-staging-postgres --project "$K2_GCP_PROJECT_ID"
git diff --check
git status --short --branch
```

Do not enable shell tracing while handling secrets or Workload Identity Federation configuration. Do not paste secret-bearing command output into this file.

## Manual Validation

1. Open both environments' Cloud Run URLs and verify the intended stakeholder/demo page and public health route.
2. Open a pull request touching backend and Terraform code; confirm CI checks run and report status before merge.
3. Merge to `main`; confirm the `dev` environment redeploys automatically and passes its smoke checks.
4. Dispatch the staging-promotion workflow; confirm the identical image digest deploys to `staging` without a rebuild.
5. Trigger each of the four Cloud Run Jobs manually; confirm `JobRunLog` entries and downstream data match expectations.
6. Wait for (or simulate) each Cloud Scheduler cadence and confirm the corresponding Job fires without manual action.
7. Inspect Cloud Logging using a request correlation ID across both the Cloud Run service and a Cloud Run Job execution.
8. Confirm the uptime check, alert policies, and dashboards for both environments.
9. Resolve the `staging` custom HTTPS domain in a browser and confirm a valid certificate.
10. Execute the Cloud SQL PITR restore drill against `staging`: capture state, force a change, restore, verify.
11. Dispatch the rollback workflow once against `dev` and confirm the previous revision/state returns correctly, then redeploy the intended revision.
12. Inventory every running billable K2 resource per environment and record status, sizing, and cost assumptions in the Obsidian handoff.

## Merge Gate

K2 is merge-ready only when repository checks and the complete live GCP test matrix pass for **both** `dev` and `staging`; Terraform is the sole provisioning mechanism with no manual out-of-band resource; every scheduled task runs exclusively through Cloud Run Jobs with `K1DeploymentGuard`'s successor guard verified; the CI/CD pipeline has deployed to `dev` automatically and promoted to `staging` manually at least once, including a real rollback dispatch; the Cloud SQL PITR restore drill against `staging` has executed and verified successfully; monitoring/alerting and the staging custom domain are active; authenticated core behaviour and decision-support disclaimers regress cleanly in both environments; exact sanitized evidence is recorded; and the diff contains no unrelated changes.

**The merge gate proves the pattern, not a standing deployment.** After live evidence above is captured, both environments are torn down (`terraform destroy`, `staging` then `dev`) and a project-wide sweep confirms zero remaining billable resources — mirroring K1's closure discipline. K2 is complete once this verify-then-teardown cycle is fully evidenced; leaving `dev`/`staging` continuously live under the CI/CD pipeline is a separate "official deploy" the user authorizes explicitly in a later session, not implied by this merge gate.

## Validation Evidence

### Repository and Local Evidence — 2026-08-26

- Complete backend suite: PASS — 466 tests, zero failures, zero errors, zero skipped (up from 452 at K1's closure; +14 net from the Cloud Run Jobs migration's new interface/guard/entry-point classes and tests).
- New/changed backend code for the Cloud Run Jobs migration (plan.md Group 5): all 8 `@Scheduled` job classes (`BulkProfileSyncJob`, `BulkFundamentalsSyncJob`, `BulkRatiosSyncJob`, `BulkDcfSyncJob`, `QuoteRefreshJob`, `DividendUpdateJob`, `InsiderTradingJob`, `AlertDetectionJob`) now implement `CloudRunJob`, reusing their existing `run()`/`JobRunLogger` path unchanged; `CloudRunJobEntryPoint` dispatches `--job=<key>` to the right bean; `VisApplication.main` bootstraps with `WebApplicationType.NONE` and a real process exit code in job mode; `SchedulerConfig` is now conditional on `app.jobs.scheduling-enabled` (new, separate from the pre-existing `app.jobs.enabled` runtime kill-switch) so `K2SchedulingGuard` can assert no `@Scheduled` trigger is registered in K2 mode without touching job-body execution semantics.
- Targeted tests: PASS — `K2SchedulingGuardTest` (5), `CloudRunJobEntryPointTest` (3), `K1DeploymentGuardTest` (4, updated call sites only), `JobRunLoggerTest`, `JobAdminServiceTest`, `BulkProfileSyncJobTest` (all updated for `JobsProperties`' new 4th field, no behavioural change).
- Diff hygiene: PASS — `git diff --check` on all 68 new/changed files (backend code, Terraform, GitHub Actions workflows, `.gitignore`).
- Terraform: `terraform fmt`/`validate` could **not** run locally — the CLI is not installed in this environment and could not be installed (no `brew`). A best-effort script confirmed balanced braces/parens across every `.tf` file, which is necessary but not sufficient for valid HCL. Real `terraform validate` must run before the first `apply` — either locally after installing Terraform, or via `backend-ci.yml`'s `terraform-validate` job (uses `hashicorp/setup-terraform`), which has not yet executed against real GitHub infrastructure since no PR has been opened with these changes.
- GitHub Actions workflow YAML: PASS — all 4 new workflow files (`backend-ci.yml`, `k2-deploy-dev.yml`, `k2-deploy-staging.yml`, `k2-rollback.yml`) parse as valid YAML (checked via Ruby's Psych parser, no `actionlint` available). Not linted for GitHub Actions-specific semantics (unknown context references, permissions scoping) beyond manual review.
- Local resources: none created. No `gcloud`/`terraform` command that provisions a live resource was run.

### Pending Live Evidence

- Terraform bootstrap (state bucket, Workload Identity Federation, Artifact Registry repo, API enablement — `terraform/bootstrap/`) has not run. This requires project-admin GCP credentials and is the first live action needed.
- No environment has been applied; `dev` and `staging` do not yet exist as GCP resources.
- GitHub Environments (`dev`, `staging`) and their `vars` (WIF provider/pool, deployer SA emails) are not configured — `k2-deploy-dev.yml`/`k2-deploy-staging.yml`/`k2-rollback.yml` cannot run yet.
- The CI/CD pipeline has not executed against real GCP credentials.
- Cloud Run Jobs and Cloud Scheduler triggers are defined in Terraform but not yet applied or verified against a live invocation.
- The Cloud SQL PITR restore drill has not been executed.
- The verify-then-teardown cycle (plan.md Group 9, requirements.md Decision 11) has not started.
- K2 remains incomplete and must not be marked complete or merged until the full live GCP merge gate passes for both environments.
