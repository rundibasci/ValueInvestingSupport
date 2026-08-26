# K2 Terraform — Production-Shaped GCP Platform

Spec: `specs/2026-08-26-k2-production-shaped-gcp-platform/`. Read `requirements.md` and `plan.md` before applying anything here.

## Layout

```
terraform/
  bootstrap/            one-time, project-level resources (see below) — its own state, no remote backend
  modules/               reusable building blocks, one per GCP concern
  environments/
    dev/                 references the modules above with dev's variable values
    staging/              references the modules above with staging's variable values
```

Directory-per-environment, not Terraform workspaces (`requirements.md` Decision 2) — `dev` and `staging` have fully independent state, VPC, Cloud SQL, Redis, Cloud Run service, service accounts, and secrets. No mutable infrastructure is shared between them; the only shared resources are the ones created once in `bootstrap/` (state bucket, WIF pool/provider, Artifact Registry repo, enabled APIs).

## First-time setup (run once, in order)

1. **Bootstrap.** From `terraform/bootstrap/`, with an operator identity that has org/project admin rights (this step precedes any Workload Identity Federation, so CI cannot do it):
   ```bash
   cd terraform/bootstrap
   terraform init
   terraform apply -var="project_id=vis-version0" -var="github_repository=rundibasci/ValueInvestingSupport"
   terraform output   # note state_bucket_name, workload_identity_pool_name, workload_identity_provider_name
   ```
   This state file is small and rarely changes — it is **not** stored in the GCS bucket it creates (that would be circular). Keep it safe; do not delete casually.

2. **Configure GitHub.** In the repository's `dev` and `staging` GitHub Environments, set the `vars` referenced by `.github/workflows/k2-*.yml`: `K2_WORKLOAD_IDENTITY_POOL_NAME`, `K2_DEV_WORKLOAD_IDENTITY_PROVIDER` / `K2_STAGING_WORKLOAD_IDENTITY_PROVIDER`, `K2_DEV_DEPLOYER_SA_EMAIL` / `K2_STAGING_DEPLOYER_SA_EMAIL` (the latter two are only known after step 3, since the deployer service accounts are created by the `iam` module — expect one apply to create them, then fill in the GitHub vars, per the usual chicken-and-egg of bootstrapping WIF).

3. **Per environment**, copy `terraform.tfvars.example` to `terraform.tfvars` (gitignored) and fill in real values, then:
   ```bash
   cd terraform/environments/dev   # or staging
   terraform init
   terraform plan
   terraform apply
   ```

## Everyday use

Once bootstrapped, deployments happen through CI/CD (`.github/workflows/k2-deploy-dev.yml` on every push to `main`, `k2-deploy-staging.yml` via manual dispatch) — see `plan.md` Group 6. Running `terraform apply` by hand locally is for initial setup, investigation, and the Group 9 live-acceptance/restore-drill/teardown cycle, not routine deploys.

## Teardown

Per `requirements.md` Decision 11: the first live pass through this configuration is verify-then-teardown, not a standing deployment.

```bash
cd terraform/environments/staging && terraform destroy
cd terraform/environments/dev && terraform destroy
```

Never run `terraform destroy` without explicit user approval, and only after the acceptance evidence in `validation.md` has been captured. `terraform/bootstrap/` is **not** torn down by this cycle — the state bucket, WIF pool, and Artifact Registry repository are meant to persist across repeated verify/teardown/official-deploy cycles.
