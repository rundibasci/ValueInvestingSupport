# K1 Stakeholder Cloud Deployment — Plan

## 1. Establish the K1 Deployment Boundary

1. Document the GCP project, region, stakeholder access model, budget guardrails, and named operator.
2. Inventory runtime configuration, ports, writable paths, health dependencies, scheduled jobs, and startup/Flyway behavior.
3. Confirm that K1 uses one controlled Cloud Run instance so existing in-process scheduled work cannot execute concurrently.
4. Record which demo surface is served with the Spring Boot application and the exact stakeholder walkthrough URL.
5. Define the manual deployment and rollback checkpoints before provisioning resources.

## 2. Produce the Production Container

1. Add a reproducible multi-stage container build for the Java 21 Spring Boot service and bundled static/demo assets.
2. Run the service as a non-root user, honor Cloud Run's injected `PORT`, and expose the Actuator health endpoint.
3. Keep credentials, environment-specific configuration, build caches, test artifacts, and local `.env` files out of the image.
4. Add container-level smoke checks for startup, HTTP availability, static assets, and graceful shutdown.
5. Publish an immutable, traceable image to Artifact Registry using a commit-derived tag.

## 3. Provision Managed Data Services

1. Provision a non-production Cloud SQL for PostgreSQL instance and database with a least-privilege application identity.
2. Provision Memorystore for Redis and configure connectivity from Cloud Run.
3. Configure the application datasource, Redis endpoint, connection pools, and timeouts for managed services.
4. Run Flyway once through the controlled deployment path and record the applied schema version.
5. Verify immutable financial history, application-owned data, cache behavior, and refresh-token behavior against the managed services.

## 4. Configure Secrets and Runtime Identity

1. Create a dedicated Cloud Run service account with only the permissions required by K1.
2. Store FMP, JWT, SMTP, and other confidential runtime values in Secret Manager outside images, source control, logs, and deployment documentation.
3. Grant the runtime identity access only to the required secret versions and managed-service connections.
4. Inject secret references and non-secret configuration separately at runtime.
5. Exercise secret/configuration failure paths and confirm diagnostics do not disclose values.

## 5. Deploy the K1 Cloud Run Service

1. Deploy the immutable container over HTTPS with a single-instance maximum and settings appropriate for temporary in-process scheduled jobs.
2. Connect Cloud Run to Cloud SQL and Memorystore using the simplest secure K1-compatible networking path.
3. Configure CPU, memory, request timeout, concurrency, startup probes, and minimum/maximum instances from observed application needs.
4. Restrict access to the approved stakeholder audience; do not present the service as a public commercial release.
5. Validate API, React/static demo pages, authentication, research, and administrative workflows through the deployed URL.

## 6. Add Basic Operations and Rollback

1. Emit structured application logs to Cloud Logging without credentials, tokens, raw provider payloads, or unnecessary financial/user data.
2. Configure basic uptime and health alerting for the Cloud Run URL and critical application dependencies.
3. Document how to identify the deployed image, inspect logs/health, check Flyway state, and distinguish provider degradation from infrastructure failure.
4. Document manual deployment, rollback to the previous immutable revision, and safe handling of a migration that cannot be rolled back.
5. Record K1 limitations and the K2 follow-ups for Terraform, Cloud Run Jobs/Scheduler, private production networking, backups/PITR, CI/CD, custom domain, and production monitoring.

## 7. Validate Stakeholder Readiness

1. Run focused backend, frontend, container, and configuration checks before deployment.
2. Complete the manual QA walkthrough in `validation.md` against the deployed environment.
3. Restart the Cloud Run revision and verify PostgreSQL durability, Redis-backed behavior, and application recovery.
4. Simulate an unavailable market-data provider and confirm cache-first Yahoo fallback semantics remain intact.
5. Capture sanitized deployment evidence, unresolved risks, operator ownership, and rollback results before merge.
