# K1 — Validation

## Acceptance Criteria

- [ ] A commit-addressed production image builds reproducibly and starts as a non-root process.
- [ ] The image contains no committed or layered credentials and accepts all sensitive configuration through Secret Manager references.
- [ ] The Cloud Run service starts with `SPRING_PROFILES_ACTIVE=prod`, binds to the supplied `PORT`, and serves the existing stakeholder/demo pages over HTTPS.
- [ ] Cloud SQL PostgreSQL and Memorystore Redis are reachable from Cloud Run without public Redis exposure.
- [ ] Flyway applies successfully during deployment and a migration/startup failure does not receive traffic.
- [ ] `GET /actuator/health` is usable by the uptime check and confirms required managed dependencies without exposing sensitive details.
- [ ] Runtime IAM is least privilege and has no project-wide Owner or Editor role.
- [ ] FMP, JWT, database, and enabled SMTP secrets are injected from Secret Manager and absent from source, image layers, logs, command evidence, and notes.
- [ ] Cloud Run is configured with `max-instances=1`, and only one copy of in-process scheduled work can execute.
- [ ] Structured logs contain request correlation and sanitized failures in Cloud Logging.
- [ ] A basic HTTPS uptime check and alert policy are active and connected to the configured notification channel.
- [ ] A controlled stakeholder can authenticate and complete an authenticated read plus a core analysis/demo flow.
- [ ] Database state survives a Cloud Run revision restart and Redis/cache behaviour works against Memorystore.
- [ ] Provider/fallback provenance remains correct and the MiFID II disclaimer remains present on valuation/score/recommendation outputs.
- [ ] An actual rollback to a previous healthy revision succeeds, smoke checks pass, and the intended revision is restored.
- [ ] Runbooks cover provisioning, deployment, validation, rollback, resource inventory, cost assumptions, and authorized cleanup.
- [ ] The environment is explicitly labelled and documented as internal/stakeholder-only, not commercial production.

## Repository Test Matrix

| Scenario | Expected result |
|---|---|
| Production-profile configuration with required values | Application context starts and uses supplied managed-service endpoints |
| Missing required production secret/config | Startup fails clearly without printing the value |
| Cloud Run-provided `PORT` | HTTP server binds to the supplied port |
| Scheduler guard enabled for K1 | Schedules remain enabled only under the documented single-instance contract |
| Unsafe multi-instance deployment input | Deployment/preflight rejects or clearly blocks it |
| Health response | Required status is available; credentials and sensitive topology are absent |
| Container user inspection | Runtime process is not root |
| Container local smoke | Image starts and health endpoint responds under a safe test configuration |
| Script rerun against existing resources | Existing resources are detected; no silent destructive replacement occurs |
| Missing secret version | Deployment stops before creating a broken revision |
| Shell/static checks | Scripts are syntactically valid and do not echo sensitive values |

## Live GCP Test Matrix

| Scenario | Expected result |
|---|---|
| Initial Cloud Run deployment | Revision becomes ready and managed HTTPS URL responds |
| Cloud SQL connection | Flyway completes and database health is `UP` |
| Memorystore connection | Redis health/cache operations succeed without public exposure |
| Invalid migration/startup configuration | Revision is unhealthy and receives no production traffic |
| Public health request | Only intended health information is returned |
| Unauthenticated business API request | Existing authentication boundary rejects access |
| Stakeholder login | Controlled account receives valid JWT and role semantics remain unchanged |
| Authenticated research/analysis flow | Expected data, provenance, and disclaimer are returned |
| Revision restart | Durable database state remains; service recovers cleanly |
| Cache cold/warm requests | Memorystore is used and responses remain type-compatible |
| FMP unavailable/test fallback condition | Yahoo fallback/provenance is observable without secret/raw-payload leakage |
| Cloud Logging query | Correlation ID and structured event fields are visible; secrets are absent |
| Uptime failure simulation or policy inspection | Alert policy is correctly wired and evidence is recorded safely |
| Instance configuration inspection | Maximum instance count is exactly one |
| Rollback to prior revision | Traffic moves to known-good revision and smoke tests pass |
| Restore intended revision | Intended revision regains traffic and smoke tests pass |

## Security and Secret Checks

- [ ] `git diff` and tracked-file search contain no credential values or private key bodies.
- [ ] Container history/export inspection reveals no credential values or local credential files.
- [ ] Cloud Run environment inspection shows secrets as Secret Manager references rather than plaintext values.
- [ ] Runtime service account roles match the documented minimum permissions.
- [ ] Cloud SQL and Redis are not exposed through unintended public endpoints.
- [ ] Application and deployment logs contain no JWTs, passwords, API keys, SMTP credentials, provider payloads, or database connection secrets.
- [ ] Health and error responses contain no stack traces or sensitive infrastructure identifiers.

## Regression Checks

- [ ] Existing backend tests pass or unrelated failures are evidenced precisely.
- [ ] Existing local/demo Docker workflows remain usable.
- [ ] Authentication and role authorization behave identically to the pre-K1 application.
- [ ] Flyway remains compatible with a clean database and the deployed schema.
- [ ] Valuation, scoring, recommendation, freshness, provenance, and fallback semantics are unchanged.
- [ ] All fair-value, score, and recommendation outputs retain the MiFID II decision-support disclaimer.
- [ ] Existing static demo pages load and call the deployed backend correctly.
- [ ] Existing scheduled-job observability remains intact under the one-instance constraint.
- [ ] No user-owned portfolio, watchlist, alert, or account data is exposed across users.

## Verification Commands

Use the exact scripts and variables established during implementation; record sanitized results under Validation Evidence. At minimum:

```bash
cd backend && ./mvnw test
docker build -t vis-backend:k1-local backend
docker inspect vis-backend:k1-local
gcloud run services describe "$K1_CLOUD_RUN_SERVICE" --region "$K1_GCP_REGION" --project "$K1_GCP_PROJECT_ID"
gcloud sql instances describe "$K1_CLOUD_SQL_INSTANCE" --project "$K1_GCP_PROJECT_ID"
gcloud redis instances describe "$K1_REDIS_INSTANCE" --region "$K1_GCP_REGION" --project "$K1_GCP_PROJECT_ID"
git diff --check
git status --short --branch
```

Do not enable shell tracing while handling secrets. Do not paste secret-bearing command output into this file.

## Manual Validation

1. Open the managed Cloud Run HTTPS URL and verify the intended stakeholder/demo page and public health route.
2. Log in as a controlled stakeholder and verify role-protected navigation and one authenticated research read.
3. Run a core quick-analysis or equivalent demo flow and verify price/fair value/provenance/disclaimer behaviour.
4. Confirm a persisted record remains after deploying or restarting a revision.
5. Exercise a cold and warm cache path and confirm Redis remains healthy and type-compatible.
6. Inspect Cloud Logging using a request correlation ID and confirm operational traceability without sensitive data.
7. Confirm the uptime check and alert route; use a safe policy inspection or bounded failure exercise.
8. Inspect Cloud Run scaling and verify `max-instances=1`.
9. Route traffic to the previous known-good immutable revision, repeat health/login/core smoke checks, then restore the intended revision and repeat them.
10. Inventory every running billable K1 resource and record status, sizing, and cleanup authorization in the Obsidian handoff.

## Merge Gate

K1 is merge-ready only when repository checks and the complete live GCP test matrix pass; the production image is immutable and secret-free; Cloud SQL, Memorystore, Secret Manager, IAM, logging, monitoring, and the one-instance constraint are verified; authenticated core behaviour and decision-support disclaimers regress cleanly; a real revision rollback and restoration succeed; exact sanitized evidence is recorded; all running paid resources and cost assumptions are handed off; and the diff contains no unrelated changes.

## Validation Evidence

### Repository and Local Evidence — 2026-08-25

- K1 guard targeted test: PASS — `K1DeploymentGuardTest` (4 tests).
- Complete backend suite: PASS — 452 tests, zero failures, zero errors, zero skipped.
- Shell syntax: PASS — `bash -n scripts/gcp/*.sh`.
- Diff hygiene: PASS — `git diff --check`.
- Production image build: PASS — local image `vis-backend:k1-local`, image ID
  `sha256:508313dc9439ca10661f4bfaed4b94768343db29b06803cf49193726a35e9390`.
- Runtime image identity: PASS — configured user/group `10001:10001`.
- OCI metadata: PASS — local validation labels included version `k1-local` and base revision `10d4341`.
- Container health smoke: PASS — the image started with the isolated demo profile, local Redis,
  and scheduled-job health disabled; `GET /actuator/health` returned `{"status":"UP"}`.
- Secret-path ignore checks: PASS — K1 local config, service-account JSON patterns, and the
  Vertex live-test profile resolve to explicit `.gitignore` rules.
- Secret-content review: no new K1 secret value or private key body was added. Existing test/demo
  fixtures contain known non-production JWT key material and are outside the K1 change set.
- Local resources: the temporary `vis-k1-smoke` container was removed. The repository's existing
  `valueinvestingsupport-redis-1` local container is running; no paid cloud resource was created.

### Pending Live GCP Evidence

- `gcloud` is not installed in the current local environment, so CLI help/dry-run execution was not
  available. Script syntax and command contracts were reviewed against current official Google Cloud
  CLI documentation.
- Project, billing account, region, stakeholder invoker model, notification channel, and current cost
  estimate remain intentionally unresolved until the pre-deploy review with the user.
- Artifact Registry publication, managed-resource provisioning, secret-version creation, Cloud Run
  deployment, managed health checks, authenticated smoke tests, monitoring/alert verification,
  rollback, and restoration have not run.
- K1 remains incomplete and must not be marked complete or merged until the full live GCP merge gate passes.
