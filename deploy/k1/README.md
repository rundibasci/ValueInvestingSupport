# K1 Stakeholder Cloud Deployment

This directory contains the manual, repeatable deployment assets for roadmap phase K1. K1 is an internal evaluation environment, not a production release.

## Files

- `k1-bootstrap.ps1` creates the regional Artifact Registry repository, runtime service account, Cloud SQL instance/database/user, Serverless VPC Access connector, Memorystore instance, and empty Secret Manager resources.
- `k1-deploy.ps1` builds an immutable full-stack image and deploys one Cloud Run instance with pinned secret versions.
- `k1-rollback.ps1` routes all traffic to a named known-good revision after verifying it belongs to the service.
- `k1-validate.ps1` checks the deployed configuration and performs unauthenticated HTTPS health/static smoke tests.
- `runbook.md` contains prerequisites, secret-loading instructions, manual QA, operations, and rollback guidance.

The scripts require PowerShell 7, `gcloud`, an authenticated operator, and explicit project/region parameters. They never accept secret values as command-line arguments.
