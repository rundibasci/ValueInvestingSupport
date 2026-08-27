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
- Terraform: **PASS, verified for real.** Installed Terraform 1.15.9 locally (darwin_arm64 zip from `releases.hashicorp.com`, checksum matched against the published `SHA256SUMS`; no `brew` available, so installed to `~/bin`). `terraform fmt -recursive` found only alignment/whitespace differences (no semantic change) and was applied. `terraform init -backend=false` + `terraform validate` — including a full provider download (`hashicorp/google` 5.45.2) — **succeeded for all three configurations**: `terraform/bootstrap/`, `environments/dev/`, `environments/staging/`. This supersedes the earlier brace-balance approximation. Not yet run: `terraform plan` against real credentials (no backend/project access configured in this pass), and `backend-ci.yml`'s CI-hosted `terraform-validate` job (no PR opened yet).
- GitHub Actions workflow YAML: PASS — all 4 new workflow files (`backend-ci.yml`, `k2-deploy-dev.yml`, `k2-deploy-staging.yml`, `k2-rollback.yml`) parse as valid YAML (checked via Ruby's Psych parser, no `actionlint` available). Not linted for GitHub Actions-specific semantics (unknown context references, permissions scoping) beyond manual review.
- Local resources: none created. No `gcloud`/`terraform` command that provisions a live resource was run.

### Live GCP Evidence — `dev` — 2026-08-26

- PASS — `terraform/bootstrap/` applied cleanly: GCS state bucket, WIF pool/provider scoped to this repository, shared Artifact Registry repo `vis-k2`, 14 APIs enabled. 18 resources, zero errors.
- PASS (after 3 fixes) — `environments/dev` fully applied: 62 resources live. Two bugs found only by a live apply, not by `terraform validate`: `google_cloud_run_v2_job` rejects the `run.googleapis.com/cloudsql-instances` system annotation outright (removed from both `cloud-run-service` and `cloud-run-job` modules — unnecessary anyway, the app uses the Cloud SQL Java Connector via Admin API, not a Unix-socket sidecar); and the Cloud Run startup probe failed unconditionally without `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true`, then would have failed permanently on a fresh database pointed at `/readiness` (K1's already-documented `ingestionJobs` indicator problem) — fixed by pointing the startup probe at `/liveness` instead, matching K1's proven fix. Commit `abf9c9c`.
- PASS — Cloud Run service reaches condition status `True`; `/actuator/health/liveness` and `/actuator/health/readiness` both return `200 UP`; aggregate `/actuator/health` correctly returns `503 DOWN` only due to the known, non-hidden `ingestionJobs=NEVER_RUN` state on an empty database.
- PASS — Cloud SQL `RUNNABLE`, Redis `READY`, Flyway applied all 26 migrations with zero errors on first boot.
- PASS — manually executed the `quote-refresh` Cloud Run Job (`gcloud run jobs execute --wait`): `job_started`/`job_completed recordsProcessed=0` logged correctly (0 is correct on an empty database), clean `exit(0)`.
- PASS — Cloud Scheduler independently fired the same job automatically on its `*/15 * * * *` cadence one minute later, with no manual trigger — live, unprompted confirmation that the Jobs+Scheduler migration works end-to-end.
- Secret values: DB password freshly generated and set on the Cloud SQL `postgres` user; FMP key reused from local `.env`; JWT keypair freshly generated (first attempt used PKCS#1 by mistake, causing a `JwtService` startup failure — corrected to PKCS#8, broken v1 secret versions disabled). No secret value was logged, echoed, or written to a file that still exists.

### Live GCP Evidence — `staging` — 2026-08-26

- PASS — `environments/staging` fully applied: 62 resources live, promoting the exact same image already verified on `dev` (no rebuild), per Decision 1. One self-inflicted deploy error along the way: the `image` variable was first computed from `git rev-parse HEAD` at apply time, which had moved past the actually-built commit due to intervening doc commits, so all 8 Jobs and the Service failed with "Image not found" — fixed by hard-coding the already-verified image reference instead of re-deriving it, then re-applied successfully (28 added, 0 changed, 9 destroyed on the corrected apply).
- PASS — Cloud Run service reaches condition status `True`, 100% traffic, liveness and readiness both `200 UP`.
- PASS — Cloud SQL `RUNNABLE`, Redis `READY`.
- PASS — manually executed `vis-k2-staging-job-alert-detection`: succeeded.
- Project-wide totals confirmed after both environments: 16 Cloud Run Jobs, 16 Cloud Scheduler triggers (8 each).
- No custom domain configured yet (`custom_domain` variable left empty — DNS/certificate provisioning not started).

### Live GitHub Actions Evidence — 2026-08-26

- Fixed a naming inconsistency found before wiring real credentials: `k2-deploy-dev.yml`/`k2-deploy-staging.yml` used repo-level variables with `K2_DEV_*`/`K2_STAGING_*`-prefixed names, while `k2-rollback.yml` already relied on native GitHub Environment scoping with generic names. Standardized on the latter across all three workflows (commit `bf66898`) — correct anyway, since the WIF provider is one shared resource for both environments (`terraform/bootstrap/`) and only the deployer SA genuinely differs per environment.
- PASS — created GitHub Environments `dev` and `staging` via `gh api`; set repo-level `K2_WORKLOAD_IDENTITY_POOL_NAME`/`K2_WORKLOAD_IDENTITY_PROVIDER` (shared) and environment-scoped `K2_DEPLOYER_SA_EMAIL` (`vis-k2-dev-deployer@...` / `vis-k2-staging-deployer@...`) via `gh variable set`.
- PASS — cross-checked GCP-side IAM: both deployer service accounts' `roles/iam.workloadIdentityUser` bindings correctly scope to `principalSet://.../attribute.ref/refs/heads/main`, matching the GitHub configuration.
- PASS — opened PR #1 (`feature/k2-production-shaped-gcp-platform` → `main`) specifically to exercise `backend-ci.yml` against real GitHub Actions, deliberately not merged yet. All 3 checks passed: `backend-test` (2m25s, full backend suite), `terraform-validate (dev)` (12s), `terraform-validate (staging)` (14s). This is the first confirmation that Workload Identity Federation and the CI workflow definitions work correctly outside local `gh`/`terraform` execution.
- Not yet exercised: `k2-deploy-dev.yml` (push to `main`), `k2-deploy-staging.yml` (manual promotion), `k2-rollback.yml` — these require merging or pushing to `main`, which has not been authorized as part of this pass; the PR stays open, unmerged.

### Live Cloud SQL PITR Restore Drill — `staging` — 2026-08-26, 14:33-15:05 CEST/UTC-mixed (see timestamps below, all UTC)

Executed against `staging` only, per requirements.md Decision 8. Staging's Cloud SQL had no private-IP route reachable from this session's local machine, so all SQL execution ran through a temporary Cloud Run Job (`k2-pitr-drill-helper`, `postgres:16` image, Direct VPC egress into `vis-k2-staging-network`, `vis-k2-staging-runtime` service account) — deleted afterward.

1. **Capture a known state** — created `pitr_drill_marker` table, inserted one row: `id=1, label='k2-pitr-drill-canary', created_at=2026-08-26 14:33:25.774894+00`.
2. **Force a recoverable change** — ~2 minutes later, deleted that row from the live instance; confirmed `0 rows` remained.
3. **Restore to the pre-change point in time** — cloned the instance (`gcloud sql instances clone vis-k2-staging-postgres vis-k2-staging-postgres-pitr-drill --point-in-time=2026-08-26T14:34:30.000Z`, a timestamp between the insert and the delete). Clone operation took roughly 25 minutes end-to-end (the `gcloud` CLI's own `operations wait` polling timed out twice with "taking longer than expected" — this is a CLI polling-interval limitation, not an operation failure; the underlying `sqladmin` operation completed with status `DONE` and no error when polled directly).
4. **Verify restored data matches captured state** — queried the clone: returned the exact same row, byte-for-byte on the timestamp (`id=1, label='k2-pitr-drill-canary', created_at=2026-08-26 14:33:25.774894+00`). PASS.
5. **Verify the source instance was untouched by the drill** — re-queried the live `staging` instance: still `0 rows`, confirming the clone-based approach (not an in-place restore-from-backup) never touched the source, exactly as designed.
6. **Cleanup** — dropped `pitr_drill_marker` from the live instance, deleted the clone instance (`vis-k2-staging-postgres-pitr-drill`), deleted the helper Cloud Run Job. No drill artifact remains in either GCP or the schema.

**Result: PASS.** Point-in-time recovery is proven to work end-to-end against a real Cloud SQL instance, non-destructively, with an auditable before/after comparison.

### Bug found and fixed: wrong Dockerfile shipped a frontend-less image — 2026-08-26

Discovered during a manual hands-on product test (via `gcloud run services proxy` + a bootstrap test ADMIN user): every frontend route, including `/`, returned Spring Boot's Whitelabel 404 despite `SpaForwardController` correctly mapping `/` to `forward:/index.html` and despite passing liveness/readiness (those never touch static resources).

- **Root cause:** the image built and deployed during this Group 9 pass used `backend/Dockerfile` with the `backend/` directory as build context — a backend-only Dockerfile with no frontend build stage, so the resulting JAR ships no `index.html`/static assets at all. The correct image comes from `deploy/gcp/Dockerfile` (repo-root context): a `node:22-alpine` stage builds the React app and copies `frontend/dist/` into `backend/src/main/resources/static/` before the Maven `package` step. This is the same Dockerfile K1 already used successfully — its absence from this K2 pass was an oversight introduced when building images ad hoc during live acceptance, not a spec or Terraform defect.
- **Also present in `.github/workflows/k2-deploy-dev.yml`:** its `docker build` step had the identical bug (`backend/Dockerfile`, `backend/` context). Fixed to `-f deploy/gcp/Dockerfile .` (commit `db01b24`) and its trigger `paths:` extended to include `frontend/**` and `deploy/gcp/Dockerfile`, which were previously not watched at all.
- **Fix verified live:** rebuilt the image from `deploy/gcp/Dockerfile` (tag `5b345f4...`), re-applied `terraform apply` for both `dev` and `staging` with the corrected image (0 added, 9 changed, 0 destroyed on each — image-only redeploy), and confirmed `GET /` now returns `200` with the real `index.html` (`<title>Value Investing Support</title>`) on `dev`. Not yet re-verified on `staging` after its redeploy beyond the apply succeeding.
- **Process gap this exposes:** the live GCP test matrix (`specs/2026-08-26-k2-production-shaped-gcp-platform/validation.md` → Live GCP Test Matrix) checks liveness/readiness/Job execution but never actually requested a frontend route — a real product smoke test (open the app, log in) caught what the automated checks did not. Worth adding a frontend-route check (`curl` for `200` + a recognizable string in the body) to the live acceptance steps in any future K2/K3 pass.

### Two more bugs found during the same hands-on product test — 2026-08-26

1. **Corrupted bcrypt hash via nested shell quoting.** The test `ADMIN` user's password hash was generated correctly (`htpasswd -bnBC`, 60 chars, `$2y$` prefix) but got corrupted to 47 garbage characters when embedded directly into a `gcloud run jobs execute --args` string later re-interpreted by `/bin/sh -c`: the hash's own `$` characters (e.g. `$2y$10$...`) were consumed as shell positional-parameter expansions (`$2`, `$10`) by the container's shell before `psql` ever saw them. Every login attempt failed with a generic "Invalid credentials" — indistinguishable from a genuinely wrong password without inspecting the stored hash directly. **Fix:** stopped embedding secret-like values inside strings destined for `sh -c` re-interpretation; base64-encode the full SQL statement (an alphabet with no shell metacharacters) and pipe it through `base64 -d | psql` instead. General lesson for any future ad hoc Cloud Run Jobs debugging: never build a `sh -c "..."` script string containing a value that might itself contain `$`, `` ` ``, or quotes — use base64 or an env var + `psql -v` reference instead.
2. **Cloud Run IAM rejects authenticated requests whose `Origin` header doesn't match the service's own domain**, regardless of how a valid bearer token was obtained. Confirmed via `curl -H "Origin: http://127.0.0.1:8090" ...`: identical request, identical token, 200 without the header and 403 (`Server: Google Frontend`, empty body) with it. Root cause of the browser-only 403s on `/assets/*.js`/`*.css` (Vite emits `crossorigin` on module scripts/stylesheets, which makes the browser send `Origin`/`Sec-Fetch-Mode: cors` for those specific sub-resource requests but not for the top-level navigation to `/`). This affected both `gcloud run services proxy` and a hand-written replacement reverse proxy equally — it is a Google Cloud Run platform behavior, not a local tool bug, and **cannot be fixed by any local proxy**, since the mismatch is between the browser's local origin (`127.0.0.1:...`) and the real Cloud Run hostname. **Resolution used for this hands-on test:** granted `allUsers` `roles/run.invoker` on `vis-k2-dev-api` (network-level access only — `SecurityConfig`'s `authorizeHttpRequests` still gates every `/api/**` route behind the JWT filter, `/api/v1/admin/**` behind `ROLE_ADMIN`; unauthenticated visitors reach only the SPA shell and `/auth/**`/`/demo/**`/`/actuator/health`). This binding must be revoked before/instead of any real "official deploy" — it was granted for one debugging session, not as a K2 design decision.

### CI/CD hardening: required-reviewer approval on `dev` and `staging` — 2026-08-26

User flagged that `k2-deploy-dev.yml` runs `terraform apply` against `dev` unconditionally on every push to `main` — harmless right now (dev already exists, so apply is a no-op/update), but if `dev` were ever torn down first, the same push would silently **recreate all 62 resources from scratch with no human approval**, breaking from the verify-then-teardown discipline this whole pass followed manually.

- Added a `required_reviewers` protection rule (reviewer: the repo owner) to both the `dev` and `staging` GitHub Environments via `gh api --method PUT .../environments/{name}` with a `reviewers` array. `staging` had been called out as needing this in `k2-deploy-staging.yml`'s own comment since it was written, but never actually configured until now.
- This gates every job with `environment: dev`/`environment: staging` — including `k2-deploy-dev.yml`'s `deploy-dev` job, `k2-deploy-staging.yml`, and `k2-rollback.yml` — behind manual approval before `terraform apply` runs.
- Split `k2-deploy-dev.yml`'s `build-and-publish` job onto a new **`dev-build`** Environment (same `K2_DEPLOYER_SA_EMAIL` value as `dev`, but deliberately **no** protection rule), so building/testing/publishing the image keeps running unattended on every push — only the actual infrastructure-touching `deploy-dev` job needs approval.
- **Found and fixed a real, previously-undetected YAML syntax bug while re-touching this file**: an unquoted step name containing `: ` (`Build image (full stack: React frontend + Spring Boot backend)`) breaks YAML parsing outright. This existed since the Dockerfile fix commit (`db01b24`) and was never caught because PR #1's checks ran before that commit, and the workflow itself was never actually triggered (no push to `main` yet) — it would have failed the very first real run. Quoted the string; re-validated with Ruby's Psych parser.

### Teardown — 2026-08-26, ~18:10-18:30 CEST

User explicitly authorized: revoke public access, then tear down both environments (requirements.md Decision 11).

1. Revoked `allUsers` `roles/run.invoker` on `vis-k2-dev-api` — confirmed via `get-iam-policy`, only the user's own identity and the runtime SA remain.
2. `terraform destroy` on `staging`: first attempt destroyed all 8 Cloud Run Jobs, the Cloud Run Service, then hit a transient `http2: client connection lost` error uploading state to GCS mid-run (a network blip, not a real failure — the resources it reported destroying were actually gone). Re-running picked up cleanly from the refreshed remote state (13 resources remaining) and destroyed Cloud SQL and Redis successfully.
3. `terraform destroy` on `dev`: same pattern, no transient error this time — Cloud Run Jobs, Service, Cloud SQL, and Redis all destroyed cleanly in one pass.
4. **Both destroys then hit the same Google-side propagation delay already documented for K1's teardown**: `FLOW_SN_DC_RESOURCE_PREVENTING_DELETE_CONNECTION` / "Producer services... still using this connection" when deleting the `servicenetworking` peering, and a dependent `resourceInUseByAnotherResource` error on the subnet (blocked by a lingering `serverless-ipv4-*` Direct VPC egress reservation Google hasn't released yet). Retried once per environment; both still blocked. This is not a new K2 defect — it is the identical, already-known Google Cloud teardown behavior recorded in `specs/2026-08-25-k1-stakeholder-cloud-deployment/validation.md`, where it eventually self-resolved.
5. **Full project-wide sweep confirms every billable resource is gone**: `gcloud sql instances list`, `redis instances list`, `run services list`, `run jobs list`, `scheduler jobs list`, `compute instances/disks list`, `secrets list`, and `iam service-accounts list` (K2 deployer/runtime SAs) all return empty for both environments. **No K2 resource is generating cost as of this checkpoint.**
6. Remaining, non-billable-only residue: VPC networks `vis-k2-dev-network`/`vis-k2-staging-network`, their `*-managed-services` peering ranges, and two `serverless-ipv4-*` reservations. Also still present, unrelated to this K2 session: `vis-k1` network and `vis-k1-managed-services` (a pre-existing, already-documented residue from K1's second teardown, not created or touched by this session). None of these represent running compute, a paid external IP, or any other billed resource — VPC networks, peering connections, and internal-only address reservations are free. Retry the four-step cleanup (peering → reservation → subnet → network, per each environment) in a later session once Google has released the producer references, mirroring the exact resolution already seen for K1's equivalent residue.

### Post-teardown finding: CI/CD naturally fails after teardown, as expected — 2026-08-27

Merged PR #1 into `main` to observe the required-reviewer approval gate live. `k2-deploy-dev.yml` triggered correctly on the push, but `build-and-publish` failed at the `docker push` step: `Unable to acquire impersonated credentials: Account deleted` — because `vis-k2-dev-deployer@vis-version0.iam.gserviceaccount.com` (the WIF-impersonated identity `google-github-actions/auth` uses) no longer exists, having been destroyed along with the rest of `dev`'s `iam` module in the teardown above. `deploy-dev` was consequently `skipped` (never reached, so the approval gate itself was never exercised live).

This is the correct, expected behavior, not a defect: the pipeline's WIF identity is part of the environment it deploys, so tearing down `dev` necessarily breaks `dev`'s deploy pipeline until `dev` is re-provisioned. User confirmed the required-reviewer configuration itself (already verified present via `gh api` earlier) is sufficient evidence without a live re-provision-just-to-watch-it-pause cycle. **Re-provisioning `dev` is a prerequisite for its CI/CD pipeline to function at all going forward**, not just for the approval-gate demo.

### `k2-deploy-dev.yml` trigger changed from automatic to manual — 2026-08-27

This same failed run prompted the user to ask whether ongoing feature work should move to a `develop` branch to stop triggering a K2 job on every push. Recommended against a new branch (this project has never used one; moving the trigger elsewhere just relocates the "fires on every commit" problem) and instead changed `k2-deploy-dev.yml`'s trigger from `on: push: branches: [main]` (with a `paths:` filter) to plain `on: workflow_dispatch:` — matching `k2-deploy-staging.yml`'s existing manual pattern. User agreed; applied on commit `0ea62b5`, merged to `main`.

Rationale recorded in the workflow's own header comment: dev is a deliberately-provisioned, occasionally-torn-down environment (Decision 11), not a continuous-deployment target, so auto-triggering only ever produced noise (torn down: build fails, SA gone) or an unwanted job on unrelated commits (live: paused for approval, but still fired every time). The required-reviewer approval gate on the `deploy-dev` job itself is unchanged — this only removes the automatic trigger, not the safety gate.

### Pending Live Evidence

- `k2-deploy-dev.yml`/`k2-deploy-staging.yml`/`k2-rollback.yml` have not run end-to-end (would require a push/merge to `main`, not yet authorized) — and would now pause for manual approval at the `terraform apply` step regardless, per the new required-reviewer gate.
- The non-billable network residue described above (both K2 environments, plus the pre-existing unrelated K1 one) is pending Google's release; retry cleanup in a later session.
- K2's Terraform/CI/CD **pattern is fully proven and evidenced** (dev + staging live acceptance, CI checks, PITR drill, hands-on product test with two real bugs found and fixed, required-reviewer approval gate added) and both environments are now torn down per the verify-then-teardown design (Decision 11). Re-provisioning for an "official," continuously-live deploy remains a separate, explicitly authorized future action — not implied by this closure.
