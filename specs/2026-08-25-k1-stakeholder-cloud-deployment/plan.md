# K1 — Implementation Plan

## 1. Deployment Contract and Configuration

1. Inventory the existing Dockerfile, production Spring profile, environment variables, Flyway startup, Actuator exposure, logging, scheduled jobs, and static stakeholder pages.
2. Define validated K1 configuration inputs for GCP project, region, resource prefix, service names, image reference, database, Redis, secrets, notification channel, and stakeholder access model.
3. Add a scheduler/deployment guard that makes the K1 single-instance constraint explicit and testable without changing current local behaviour.
4. Ensure the application binds to Cloud Run's `PORT`, trusts forwarded headers safely, shuts down gracefully, and exposes only the intended health information.
5. Update `.env.example` and operational documentation with names/placeholders only; never add secret values.

## 2. Production Container

1. Review and harden `backend/Dockerfile` for reproducible multi-stage construction, non-root execution, small runtime surface, Java 21 compatibility, and container-aware JVM settings.
2. Add image metadata and an immutable version/revision strategy tied to the Git commit.
3. Add or update ignore rules so local credentials, build products, and GCP key files cannot enter the build context.
4. Build the image locally and run non-secret startup/health smoke checks.
5. Scan or inspect the resulting image for obvious embedded secrets and document remaining scanner limitations.

## 3. GCP Provisioning Scripts

1. Add a preflight script that checks `gcloud` authentication, active project/region inputs, required CLIs, billing/API prerequisites, and avoids implicit project mutation.
2. Add rerunnable commands/scripts to enable APIs and create Artifact Registry, application runtime identity, Cloud SQL PostgreSQL, application database/user path, Memorystore Redis, networking/connectivity, and required Secret Manager secret containers/references.
3. Add least-privilege IAM bindings for the runtime identity and keep deployer permissions documented separately.
4. Require secret versions to exist before deploy while accepting values only through operator-controlled, non-committed, non-echoed mechanisms.
5. Add resource inventory and estimated-cost guidance before provisioning billable resources.

## 4. Build, Publish, and Deploy

1. Build and test the backend before publishing an image.
2. Configure Docker authentication for Artifact Registry and publish an immutable commit-addressed image.
3. Deploy Cloud Run with the production profile, managed-service connectivity, Secret Manager bindings, resource limits, health/startup probes, and `max-instances=1`.
4. Configure the agreed ingress/invoker policy and record the deployed URL and revision without exposing tokens.
5. Confirm Flyway completes, the revision becomes ready, and no traffic is promoted to a failed startup.

## 5. Logging, Monitoring, and Cost Guardrails

1. Verify structured, correlated, sanitized application logs in Cloud Logging.
2. Configure an HTTPS uptime check for the intended health route and an alert policy attached to a pre-existing or explicitly configured notification channel.
3. Document useful Cloud Run, Cloud SQL, Redis, error-rate, latency, and instance-count queries for stakeholder operations.
4. Verify the deployed service remains capped at one instance and add a check to deployment validation.
5. Record running billable resources, selected sizes, cost assumptions, and cleanup commands in the runbook and Obsidian handoff.

## 6. Deployment and Rollback Runbooks

1. Document prerequisites, initial provisioning, secret-version setup, image publication, deployment, post-deploy checks, and operator responsibilities.
2. Document deployment of a new immutable revision without mutating or deleting the prior known-good revision.
3. Document and execute traffic rollback to the prior revision, verify health/core smoke tests, then restore the intended revision.
4. Document controlled teardown in dependency-safe order; never run teardown without explicit user approval.
5. Document known K1 limitations and the K2 migration path for schedules, scaling, Terraform, private connectivity hardening, CI/CD, backups/PITR, and custom domain.

## 7. Automated Verification

1. Add focused tests for Cloud Run configuration binding, scheduler guardrails, health-detail exposure, and production-profile startup where repository patterns allow.
2. Run backend targeted tests and the complete backend suite.
3. Build the production container and verify local startup/health behaviour.
4. Run static checks for shell scripts and secret-like committed content using available repository tooling.
5. Run frontend typecheck/build only if K1 changes frontend assets or their serving contract.
6. Run `git diff --check` and review the final diff for unrelated or secret-bearing changes.

## 8. Live GCP Acceptance and Handoff

1. Provision the selected non-production environment only after project, region, access, budget, and billable-resource authorization are confirmed.
2. Execute health, authentication, core analysis/demo, persistence, Redis/cache, FMP/fallback, logging, monitoring, and single-instance checks against the managed HTTPS URL.
3. Execute and evidence the rollback drill and restoration of the intended revision.
4. Update `validation.md` with exact commands, revisions, timestamps, sanitized evidence, limitations, and remaining gates.
5. Update the K1 Obsidian consistency note with branch/commit/push state, running resources, measured or estimated costs, authorization boundary, and exact next action.
6. Mark K1 complete and update the roadmap only after every merge-gate condition is satisfied.
