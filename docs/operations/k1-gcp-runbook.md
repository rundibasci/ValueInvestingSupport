# K1 GCP Stakeholder Deployment Runbook

## Purpose and Boundary

This runbook deploys the internal K1 stakeholder environment. It is not a commercial production release. K1 temporarily keeps scheduled work inside the API process, therefore Cloud Run must remain capped at exactly one instance. K2 replaces this with Cloud Run Jobs and Cloud Scheduler before horizontal scaling.

No command in this runbook should be run with shell tracing. Never paste secret values, JWTs, database passwords, API keys, or raw provider payloads into terminal transcripts, specifications, validation evidence, or Obsidian.

## Files

- Configuration template: `deploy/gcp/k1.env.example`
- Read-only preflight: `scripts/gcp/k1-preflight.sh`
- Billable provisioning: `scripts/gcp/k1-provision.sh`
- Image build/publish: `scripts/gcp/k1-build-publish.sh`
- Candidate deployment: `scripts/gcp/k1-deploy.sh`
- Read-only service verification: `scripts/gcp/k1-verify.sh`
- Monitoring setup: `scripts/gcp/k1-monitoring.sh`
- Traffic rollback/restore: `scripts/gcp/k1-rollback.sh`

## 1. Pre-deploy Decisions

Agree and record before provisioning:

1. GCP project and active billing account.
2. Region supported by Cloud Run, Cloud SQL, Memorystore, Direct VPC egress, Artifact Registry, and Monitoring. K1 uses `europe-west1`; this is not the final K3 data-residency decision.
3. Resource prefix and whether an existing network or notification channel must be reused.
4. Stakeholder access mode: `authenticated` adds Cloud Run IAM outside Spring Security; `public` exposes the URL while Spring Security still protects business APIs.
5. Notification channel resource name and tested recipient.
6. Current regional prices for Cloud SQL, Memorystore, Artifact Registry, Cloud Run, logging, and monitoring.
7. Authorization to create the billable resources listed below.

## 2. Expected Billable Inventory

| Resource | Proposed K1 size | Cost behaviour |
|---|---|---|
| Cloud SQL PostgreSQL 16 | `db-f1-micro`, 10 GB SSD, zonal | Runs continuously |
| Memorystore Redis 7 | Basic, 1 GB | Runs continuously |
| Direct VPC egress | Dedicated subnet; no connector | No connector instance charge |
| Cloud Run | 1 CPU, 1 GiB, min 0, max 1 | Usage based |
| Artifact Registry | Immutable commit images | Storage/egress based |
| Logging/Monitoring | Basic logs, uptime check, alert | Volume based |

This is an assumption, not a quote. Record a current Pricing Calculator estimate before provisioning and measured billing after deployment.

## 3. Local Configuration and Preflight

```bash
cp deploy/gcp/k1.env.example deploy/gcp/k1.env
export K1_CONFIG_FILE=deploy/gcp/k1.env
scripts/gcp/k1-preflight.sh
```

The gitignored configuration contains identifiers only, never secret values. Preflight is read-only and requires the image tag to equal the full reviewed Git SHA.

## 4. Provision Managed Resources

This changes GCP state and creates continuously billed resources. Run only after explicit approval:

```bash
export K1_CONFIRM_BILLABLE_PROVISIONING=YES
scripts/gcp/k1-provision.sh
unset K1_CONFIRM_BILLABLE_PROVISIONING
```

The script creates resource and secret containers but no secret versions. It reuses named resources and does not silently replace them.

## 5. Database User and Secret Versions

Create the PostgreSQL application user interactively. Do not put its password in shell history. Add secret versions through an interactive/no-echo or protected-file workflow approved by the operator.

Required containers hold FMP API key, database username/password, and JWT private/public keys. SMTP username/password are required only when email is enabled. Google OAuth secrets can be added later if stakeholder login uses OAuth; password/JWT login is sufficient for the K1 gate.

Verify metadata only:

```bash
gcloud secrets versions list SECRET_NAME --filter='state=ENABLED' --limit=1
```

Never access secret values for validation evidence.

## 6. Build, Publish, and Candidate Deploy

From a reviewed, committed, clean working tree:

```bash
scripts/gcp/k1-build-publish.sh
export K1_CONFIRM_DEPLOY=YES
scripts/gcp/k1-deploy.sh
unset K1_CONFIRM_DEPLOY
```

The deploy script creates a tagged candidate with no traffic, Secret Manager references, Cloud SQL attachment, Direct VPC egress, startup probe, and `max-instances=1`. Record the Git SHA, image digest, and candidate revision.

After verifying the candidate, promote only the exact revision:

```bash
gcloud run services update-traffic "$K1_CLOUD_RUN_SERVICE" \
  --region "$K1_GCP_REGION" --project "$K1_GCP_PROJECT_ID" \
  --to-revisions VERIFIED_REVISION=100
```

## 7. Monitoring

After the service URL and notification channel exist:

```bash
export K1_CONFIRM_MONITORING_CHANGES=YES
scripts/gcp/k1-monitoring.sh
unset K1_CONFIRM_MONITORING_CHANGES
```

Authenticated mode uses an OIDC token from the Monitoring service agent and grants that agent only Cloud Run Invoker on the K1 service. Verify the check becomes healthy and test alert delivery safely.

## 8. Live Smoke Matrix

Run `scripts/gcp/k1-verify.sh`, then record sanitized evidence for:

1. HTTPS health and single-instance configuration.
2. Flyway success plus PostgreSQL and Redis health.
3. Unauthenticated rejection for protected APIs.
4. Controlled stakeholder login.
5. Authenticated research read and one quick-analysis flow.
6. Correct provider/fallback provenance and MiFID II disclaimer.
7. Database persistence across a revision restart.
8. Cold/warm Redis behaviour without serialization failures.
9. Correlated structured logs without secrets, tokens, stack traces, or provider payloads.
10. Uptime status and alert delivery.

## 9. Rollback Drill and Restoration

```bash
gcloud run revisions list --service "$K1_CLOUD_RUN_SERVICE" \
  --region "$K1_GCP_REGION" --project "$K1_GCP_PROJECT_ID"
export K1_CONFIRM_TRAFFIC_CHANGE=YES
scripts/gcp/k1-rollback.sh PREVIOUS_KNOWN_GOOD_REVISION
```

Repeat health/login/core smoke tests. Restore the intended revision with the same script and repeat checks:

```bash
scripts/gcp/k1-rollback.sh INTENDED_REVISION
unset K1_CONFIRM_TRAFFIC_CHANGE
```

Rollback assumes every Flyway migration remains backward-compatible. Stop if schema compatibility is uncertain.

## 10. Resource Inventory and Cleanup

At every handoff record service/revision, database, Redis, VPC network/subnet, repository/image digest, secret containers, uptime check, alert policy, and whether each billable resource is running.

Cleanup is destructive and intentionally not automated. Execute only with explicit approval after resolving exact targets and retention needs. Prefer deleting Cloud Run and continuously billed Redis/Cloud SQL first; remove secrets, images, IAM identities, peering, subnet, and network only after confirming retention requirements.

## 11. K1 Limitations Carried to K2

- One Cloud Run instance only.
- In-process schedules can be delayed when Cloud Run scales to zero; K1 is controlled stakeholder use only.
- Manual `gcloud` workflow instead of Terraform/CI/CD.
- No custom domain, production HA, formal PITR exercise, edge protection, or commercial compliance gate.
- K2 must split web traffic from background jobs before scaling.
