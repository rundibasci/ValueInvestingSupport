# K1 Stakeholder Cloud Deployment Runbook

## Boundary

K1 provides one internal/stakeholder Cloud Run service backed by non-production Cloud SQL PostgreSQL and Memorystore Redis. The application retains its existing in-process schedules, so both revision-level and service-level maximum instances must remain `1`.

K1 is not a customer-facing production environment. Terraform, CI/CD, Cloud Run Jobs/Scheduler, production private networking, formal backup/PITR exercises, custom domains, and compliance release evidence belong to K2/K3.

## Prerequisites

- PowerShell 7, Docker for local image checks, Git, and the Google Cloud CLI.
- An authenticated operator with permission to enable APIs and manage Cloud Run, Cloud Build, Artifact Registry, Cloud SQL, Memorystore, VPC Access, Secret Manager, IAM, Logging, and Monitoring.
- A selected GCP project, billing account, region, stakeholder access model, alert owner, and budget owner.
- Cloud SQL, Memorystore, VPC connector, and Cloud Run in the same region.

Set the project explicitly before every session:

```powershell
gcloud auth login
gcloud config set project PROJECT_ID
gcloud config set run/region REGION
```

Do not place credentials in shell history, script arguments, local environment templates, screenshots, or committed files.

## 1. Bootstrap K1 resources

Preview:

```powershell
./deploy/k1/k1-bootstrap.ps1 -ProjectId PROJECT_ID -Region REGION -WhatIf
```

Create:

```powershell
./deploy/k1/k1-bootstrap.ps1 -ProjectId PROJECT_ID -Region REGION
```

The script is idempotent for named resources. It creates:

- regional Artifact Registry repository `vis-k1`;
- runtime service account `vis-k1-runtime`;
- PostgreSQL 16 Cloud SQL instance `vis-k1-postgres` and database `vis`;
- Serverless VPC Access connector `vis-k1-connector`;
- basic Redis 7 Memorystore instance `vis-k1-redis`;
- empty Secret Manager resources.

Cloud SQL user creation is intentionally manual because `gcloud sql users create` accepts its password as a command-line value. In the Google Cloud Console:

1. Open the `vis-k1-postgres` instance and create user `vis_app` with a generated password.
2. Open Secret Manager and add the same value as version `1` of `vis-k1-database-password`.
3. Clear the clipboard and discard the generated value through the organization's approved secret-handling process.

Do not pass the database password through `--password`, shell history, a committed file, or script output.

## 2. Load pinned secret version 1

Add version `1` to every empty secret. Use `--data-file`; do not pipe or pass values on the command line.

```powershell
gcloud secrets versions add vis-k1-fmp-api-key --data-file PATH --project PROJECT_ID
gcloud secrets versions add vis-k1-jwt-private-key --data-file PATH --project PROJECT_ID
gcloud secrets versions add vis-k1-jwt-public-key --data-file PATH --project PROJECT_ID
gcloud secrets versions add vis-k1-smtp-password --data-file PATH --project PROJECT_ID
```

The deploy script pins version `1`. Rotation creates a new version and a deliberate new Cloud Run revision; never silently change deployed secrets to `latest`.

## 3. Build and deploy

Use the connection name and Redis host printed by the bootstrap script:

```powershell
./deploy/k1/k1-deploy.ps1 `
  -ProjectId PROJECT_ID `
  -Region REGION `
  -CloudSqlInstance 'PROJECT_ID:REGION:vis-k1-postgres' `
  -RedisHost 'MEMORYSTORE_PRIVATE_IP'
```

The script:

- derives the image tag from the current commit;
- builds the root `Dockerfile` with Cloud Build;
- publishes the immutable image to Artifact Registry;
- deploys the `k1` profile with one maximum instance;
- keeps one instance warm with CPU allocated so the temporary in-process schedules can run;
- uses the dedicated runtime service account and VPC connector;
- injects pinned Secret Manager versions;
- prints the service URL, revision, and image.

The default `application` access mode permits the Cloud Run URL while application authentication protects user/API data. Only approved application accounts may be provisioned. If the audience can supply Cloud Run identity tokens or use a proxy, deploy with `-AccessMode cloud-run-iam` and grant `roles/run.invoker` only to approved principals.

The always-allocated single instance is an explicit K1 cost. Confirm the budget owner accepts it before deployment; K2 removes this coupling by moving schedules to independently triggered Cloud Run Jobs.

## 4. Add basic monitoring

Create an HTTPS uptime check for `/actuator/health` and an alert policy owned by the nominated K1 operator. Keep the endpoint response free of component details for anonymous callers.

At minimum, alert on:

- uptime-check failure for two consecutive checks;
- Cloud Run 5xx responses;
- container startup failures;
- Cloud SQL connection exhaustion or unavailability;
- Memorystore unavailability.

Record alert-policy names and notification-channel owners in the K1 validation report. Do not commit personal addresses or webhook credentials.

## 5. Validate

Run the configuration and smoke validator:

```powershell
./deploy/k1/k1-validate.ps1 -ProjectId PROJECT_ID -Region REGION
```

For Cloud Run IAM mode:

```powershell
./deploy/k1/k1-validate.ps1 -ProjectId PROJECT_ID -Region REGION -UseIdentityToken
```

Then execute every manual QA section in `specs/2026-07-25-GPT-k1-stakeholder-cloud-deployment/validation.md`. Capture only sanitized evidence:

- source commit and image digest;
- Cloud Run revision and region;
- health and workflow outcomes;
- Flyway schema version;
- maximum-instances configuration;
- alert delivery and rollback result;
- known limitations and owners.

Never capture tokens, secret values, raw provider payloads, database URLs containing credentials, or personal financial data.

## 6. Operate and troubleshoot

Describe the service:

```powershell
gcloud run services describe value-investing-support-k1 --region REGION --project PROJECT_ID
```

Read recent error logs:

```powershell
gcloud logging read 'resource.type="cloud_run_revision" AND resource.labels.service_name="value-investing-support-k1" AND severity>=ERROR' --limit 50 --project PROJECT_ID
```

Inspect Flyway without printing credentials by connecting through an approved Cloud SQL path and querying `flyway_schema_history`.

Classify failures before acting:

- database/Redis health failure: managed dependency or connectivity;
- FMP failure with Yahoo success: expected provider fallback;
- both providers unavailable: provider degradation, never fabricate data;
- startup failure after migration: inspect migration compatibility before rollback;
- duplicate job evidence: stop traffic and verify both service- and revision-level maximum instances are `1`.

## 7. Roll back

List revisions and identify a previously validated revision:

```powershell
gcloud run revisions list --service value-investing-support-k1 --region REGION --project PROJECT_ID
```

Route all traffic to it:

```powershell
./deploy/k1/k1-rollback.ps1 -ProjectId PROJECT_ID -Region REGION -Revision KNOWN_GOOD_REVISION
```

After rollback, rerun health, login, and one read-only research workflow. A container rollback does not reverse Flyway. If the new migration is not backward-compatible, stop and follow its documented forward-fix/recovery decision; do not manually edit schema history.

## K2 handoff

Before allowing horizontal scaling or production use, K2 must:

- move schedules to Cloud Run Jobs triggered by Cloud Scheduler;
- manage infrastructure with Terraform;
- add CI/CD and environment promotion;
- adopt production private connectivity, backups/PITR, and restore drills;
- add custom HTTPS domain, production monitoring, rate/edge controls, and formal operational ownership.
