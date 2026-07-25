# K1 — Stakeholder Cloud Deployment

Deploys the Value Investing Support platform to Google Cloud Run for internal stakeholder evaluation.

## Prerequisites

- PowerShell 7
- Docker (for local smoke tests)
- Google Cloud CLI (`gcloud`) — authenticated with permissions to manage Cloud Run, Cloud SQL, Memorystore, Artifact Registry, Secret Manager, and IAM
- Git (immutable image tag derived from commit SHA)

## Quick start

```powershell
# 1. Bootstrap GCP resources (idempotent)
./deploy/k1/k1-bootstrap.ps1 -ProjectId my-project -Region europe-west1

# 2. Create Cloud SQL user 'vis_app' manually in Google Cloud Console
#    and store the password as Secret Manager secret 'vis-k1-database-password' version 1.

# 3. Load remaining secrets
gcloud secrets versions add vis-k1-fmp-api-key --data-file /path/to/fmp-key.txt --project my-project
gcloud secrets versions add vis-k1-jwt-private-key --data-file /path/to/jwt-priv.pem --project my-project
gcloud secrets versions add vis-k1-jwt-public-key --data-file /path/to/jwt-pub.pem --project my-project
gcloud secrets versions add vis-k1-smtp-password --data-file /path/to/smtp-pass.txt --project my-project

# 4. Build and deploy
./deploy/k1/k1-deploy.ps1 `
  -ProjectId my-project `
  -Region europe-west1 `
  -CloudSqlInstance 'my-project:europe-west1:vis-k1-postgres' `
  -RedisHost '10.x.x.x'

# 5. Validate
./deploy/k1/k1-validate.ps1 -ProjectId my-project -Region europe-west1
```

## Files

| File | Purpose |
|---|---|
| `k1-bootstrap.ps1` | One-time GCP resource provisioning (idempotent) |
| `k1-deploy.ps1` | Build image with Cloud Build, deploy to Cloud Run |
| `k1-rollback.ps1` | Route traffic to a known-good revision |
| `k1-validate.ps1` | Health, revision, and log checks against deployed service |
| `runbook.md` | Full operational documentation |

## Important constraints

- **One instance maximum** — in-process `@Scheduled` jobs require single-instance execution.
- **Always-allocated CPU** — costs are fixed monthly; budget owner must accept before deployment.
- **Internal/stakeholder-only** — this is not a customer-facing production environment.
- **Secrets never in source control, images, logs, or command-line arguments.**
