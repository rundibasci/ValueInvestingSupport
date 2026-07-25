# K1 Stakeholder Cloud Deployment — Validation

## Functional Acceptance

- [ ] One immutable application image is stored in Artifact Registry and traceable to the tested source revision.
- [ ] The K1 Cloud Run revision starts successfully and serves the API plus intended React/static demo surfaces over HTTPS.
- [ ] Cloud Run is limited to one instance while in-process scheduled jobs remain enabled.
- [ ] The application connects to Cloud SQL PostgreSQL and Memorystore Redis.
- [ ] Flyway reaches the expected schema version without manual database edits.
- [ ] `/actuator/health` reports usable application, database, and Redis health.
- [ ] Approved stakeholders can access the intended application flow; unapproved access is denied.
- [ ] Secrets are injected from Secret Manager and absent from images, source, logs, and captured evidence.
- [ ] Existing decision-support disclaimers and data-availability states remain visible.

## Manual QA

Manual QA is the primary K1 acceptance method when GCP access is available.

### 1. Deployment and access

1. Record the source commit, immutable image digest, Cloud Run revision, project, and region.
2. Open the HTTPS Cloud Run URL as an approved stakeholder.
3. Confirm the intended login and application/demo surfaces load without mixed-content or asset errors.
4. Repeat as an unapproved or anonymous visitor.

Expected result: the revision is traceable, approved access succeeds, and unauthorized access follows the documented policy.

### 2. Managed dependency health

1. Inspect `/actuator/health` using the documented access path.
2. Confirm PostgreSQL and Redis connectivity without exposing credentials or connection strings.
3. Inspect Flyway history and verify the expected migration version.
4. Restart the Cloud Run revision and repeat the checks.

Expected result: the application recovers against the same managed state and reports actionable dependency health.

### 3. Stakeholder value-investing walkthrough

1. Log in using each representative role required by the demo.
2. Seed or inspect a controlled symbol set.
3. Exercise search/screener, a single-stock review, watchlist, portfolio, and ADMIN job monitoring where authorized.
4. Confirm values, source/freshness states, missing-data explanations, and MiFID II disclaimers match local behavior.
5. Confirm unauthorized role actions remain unavailable.

Expected result: the deployed environment supports the existing stakeholder workflow without changing domain behavior.

### 4. Cache and provider resilience

1. Read a controlled symbol twice and confirm warm-cache behavior.
2. Exercise the documented FMP-unavailable path and verify Yahoo fallback where supported.
3. Restart the application without deleting managed data and repeat a representative read.
4. Inspect sanitized logs for source/fallback and cache failures.

Expected result: cache-first behavior and fallback semantics survive deployment and restart without fabricated data.

### 5. Scheduled-work safety

1. Inspect Cloud Run scaling configuration and confirm maximum instances is one.
2. Observe scheduled job/run history across a deployment or restart window.
3. Trigger one authorized manual job and reconcile its events/outcome.

Expected result: no duplicate scheduled execution is caused by multiple API instances.

### 6. Observability and rollback

1. Confirm structured application logs arrive in Cloud Logging.
2. Trigger a harmless health-check failure or use an approved test condition and confirm the basic alert reaches its owner.
3. Deploy a new test revision, then follow the runbook to route traffic back to the previous known-good revision.
4. Re-run login, health, and one read-only research flow after rollback.

Expected result: operators can identify failures and restore the previous application revision without losing PostgreSQL state.

## Automated and Pre-deployment Checks

- [ ] Backend tests and build pass under Java 21.
- [ ] Frontend typecheck/tests/build pass.
- [ ] The container builds from a clean checkout.
- [ ] Container smoke tests cover startup, injected `PORT`, health, served assets, and shutdown.
- [ ] Image inspection finds no `.env`, credential, private key, test-result, or local log artifacts.
- [ ] Configuration tests cover missing required values and sanitized errors.
- [ ] `git diff --check` passes.

## Merge Readiness

- [ ] Every K1 functional acceptance item is complete.
- [ ] Manual QA evidence identifies the source commit, image digest, revision, date, tester, and sanitized results.
- [ ] Deployment and rollback instructions were followed successfully by someone other than their author where possible.
- [ ] One-instance scheduling protection is visible in deployment configuration and verified operationally.
- [ ] Secret Manager and IAM access were reviewed for least privilege.
- [ ] No credentials or sensitive payloads appear in Git history, image layers, Cloud Logging, screenshots, or QA artifacts.
- [ ] Known limitations and K2 follow-ups are documented with owners.
- [ ] The environment is clearly labeled internal/stakeholder-only.

## Risks

- A single Cloud Run instance limits availability and scaling; this is accepted only for K1.
- In-process scheduled jobs couple HTTP availability and background processing; K2 must separate them before horizontal scaling.
- Manual provisioning can drift; K2 must establish Terraform as the source of truth.
- A database migration may prevent simple revision rollback; each migration needs an explicit compatibility decision.
- Managed-service networking or cold starts may cause latency and connection-pressure issues; configuration must be measured in the deployed environment.
- Incomplete access restrictions could expose financial or user data; stakeholder authorization is a merge gate.
- Basic K1 monitoring and recovery do not satisfy commercial production requirements.
