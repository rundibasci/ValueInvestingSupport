# AGENTS.md

Repo-wide instructions for any coding agent (Codex CLI, Claude Code, etc.) working on this project. Currently holds one runbook; add further sections as needed rather than starting new top-level files.

## Reactivate / refresh the K2 `staging` GCP environment

Reactivate or refresh the K2 `staging` GCP environment (`vis-version0`, `europe-west1`, resource prefix `vis-k2-staging`) for manual testing. This repo's staging CI/CD (`k2-deploy-staging.yml`) requires a deployer service account that has been destroyed by a prior teardown more than once, so this is done **locally** instead, using the operator's own `roles/owner` gcloud credentials, bypassing CI entirely.

This section is a self-contained copy of the procedure (originally kept in Marcello's Obsidian vault, `~/Documents/valueinvestorsupport/ValueInvestingSupport/2026-09-03-K2 - Staging manual bootstrap runbook.md`, and triggered on the primary Mac via the Claude Code command `.claude/commands/reactivate-staging.md`). It's meant to work standalone on any machine with `gcloud`/`terraform`/`docker` access to the `vis-version0` GCP project — no dependency on that vault. **Read it in full before doing anything.** If you discover a new gotcha or the procedure changes, update this section (and, if you have access to it, the Obsidian vault copy too — mention the discrepancy to the user otherwise) — it's the single source of truth this section exists to keep current.

### 1. Check current state before touching anything

Run all of these before deciding what to do — don't assume staging is either fully up or fully torn down:

```bash
gcloud run services describe vis-k2-staging-api --region=europe-west1 --project=vis-version0 --format="value(status.url,status.conditions)" 2>&1
gcloud sql instances describe vis-k2-staging-postgres --project=vis-version0 --format="value(state,ipAddresses)" 2>&1
gcloud redis instances describe vis-k2-staging-redis --region=europe-west1 --project=vis-version0 --format="value(state)" 2>&1
gcloud iam service-accounts list --project=vis-version0 --filter="email:vis-k2-staging-*" --format="value(email)" 2>&1
gcloud secrets list --project=vis-version0 --filter="name:vis-k2-staging-*" --format="value(name)" 2>&1
gcloud run services get-iam-policy vis-k2-staging-api --region=europe-west1 --project=vis-version0 2>&1
curl -s -o /dev/null -w "%{http_code}\n" --max-time 10 "$(gcloud run services describe vis-k2-staging-api --region=europe-west1 --project=vis-version0 --format='value(status.url)' 2>/dev/null)/actuator/health/liveness" 2>&1
git rev-parse HEAD
```

Classify into one of three states before proceeding:

- **A. Fully torn down** (no service accounts, no Cloud Run, no Cloud SQL) → full bootstrap, steps 1-7 below in order.
- **B. Up, but stale code** (Cloud Run healthy, but its deployed image tag ≠ current `git rev-parse HEAD`) → skip straight to step 1 (build+push, matching this session's HEAD) and step 4 (force a new revision via `terraform apply -target=module.cloud_run_service`), skip step 2/3/5 entirely — the environment, secrets, and admin user already exist.
- **C. Up and current** → nothing to reactivate. Just run the health checks above, report the URL, and stop — do not touch Terraform, secrets, or the database. If asked "why isn't X working" when the service itself is healthy, check Cloud Run request logs for the actual failing path/status first (`gcloud logging read 'resource.type="cloud_run_revision" AND resource.labels.service_name="vis-k2-staging-api" AND (httpRequest.status>=400 OR severity>=ERROR)' --project=vis-version0 --freshness=15m`) before assuming an infra problem — a session-expired JWT refresh loop or similar app-level issue looks like "not responding" from the outside but isn't a reactivation task.

### 2. Confirm before spending

State A (full bootstrap) provisions real billable resources (Cloud SQL, Memorystore Redis, Cloud Run) — confirm with the user before starting unless they already explicitly asked for this specific reactivation in the current conversation. State B (redeploy only) is near-zero incremental cost (rebuilding an image and rolling a new revision of already-running infra) — proceed without asking again if the user's request to reactivate/redeploy staging is what triggered this.

### 3. Known one-off approvals this session will likely need

A couple of individual actions commonly get blocked by a coding agent's own sandbox/approval layer even when the broader command class is allowed — expect to ask the user to run these themselves, or to get an explicit one-off approval, rather than treating a block as fatal:
- `gcloud sql instances patch ... --assign-ip` (temporary public IP for the admin-bootstrap step)
- `gcloud run services add-iam-policy-binding ... --member=allUsers` (making Cloud Run publicly reachable, only needed if the user wants to test from a real browser instead of `gcloud run services proxy`)

Editing an agent's own permission/config file to add a new rule is typically blocked when the agent tries it on itself (self-granting is a hard boundary in most harnesses) — if a genuinely new class of command needs approval, ask the user to add the rule by hand (tell them exactly what to add) rather than repeatedly retrying the same blocked command.

### 4. After reactivating

Report back: the URL, which state (A/B/C) applied, whether the admin user / Google OAuth credentials still work (state B/C should not need to touch either), current Cloud Run IAM mode (private-proxy-only vs. public), and remind the user staging has a real hourly cost while left up — ask whether to tear it down when they're done testing, don't do it unprompted.

---

### Full step-by-step (what actually worked, 2026-09-03, re-provisioning from a fully torn-down state)

This is the detailed procedure for **State A** (and the relevant sub-steps for State B). Written after re-provisioning `staging` to test RM4/TA6 (AI Investment Thesis) live; the CI/CD pipeline could not be used because its WIF-impersonated deployer service account had been destroyed along with the rest of the environment in an earlier teardown — this is expected, documented behavior (see `specs/2026-08-26-k2-production-shaped-gcp-platform/validation.md`: "the pipeline's WIF identity is part of the environment it deploys").

#### Prerequisites

- **Terraform**: if not preinstalled, download the appropriate zip from `releases.hashicorp.com` to e.g. `~/bin/terraform`; `export PATH="$HOME/bin:$PATH"` per shell session if not on PATH.
- **Docker Desktop**: if not running, `open -a Docker`, wait ~5-8s, then `docker info` should succeed.
- **gcloud**: must be authenticated with `roles/owner` (or equivalent) on `vis-version0`, and ADC active (`gcloud auth application-default login`).
- **Sandbox/permission rules**: on this repo's own Claude Code setup, `.claude/settings.json` (project-level, committed) already allows `Bash(terraform *)`, `Bash(~/bin/terraform *)`, `Bash(gcloud sql *)`. On another machine/agent (e.g. Codex), check its equivalent allow-list/sandbox config and expect to need similar permissions granted by a human — the agent cannot self-grant these. Some individual actions (public IP on Cloud SQL, public IAM invoker on Cloud Run) are commonly blocked **even with a matching allow rule** and need a direct one-off approval or to be run by the human directly.

#### Step 1. Build and push the image — **must be amd64**

```bash
docker build --platform linux/amd64 -f deploy/gcp/Dockerfile -t \
  europe-west1-docker.pkg.dev/vis-version0/vis-k2/vis-backend:<full-commit-sha> .
docker push europe-west1-docker.pkg.dev/vis-version0/vis-k2/vis-backend:<full-commit-sha>
```

**Gotcha (hit repeatedly, same as K2 dev's own history):** without `--platform linux/amd64` on Apple Silicon, Cloud Run rejects the image outright: *"Container manifest type ... must support amd64/linux."* Verify before pushing: `docker inspect --format='{{.Architecture}}/{{.Os}}' <image>` must print `amd64/linux`.

#### Step 2. Terraform apply in two stages, not one

`terraform/environments/staging` needs `terraform init` first (reuses the existing GCS backend + lock file). Then apply in **two passes**, not a single full apply — Cloud Run's secret env bindings reference `versions/latest` of each Secret Manager secret, and **that reference fails hard at Cloud Run creation time if the secret has zero versions**. The secret containers only get created by `module.secret_manager`, so they must exist (and be populated) *before* `module.cloud_run_service` is applied.

```bash
export PATH="$HOME/bin:$PATH"
cd terraform/environments/staging
terraform init -input=false

# Stage 1: everything that doesn't need secret VALUES yet
terraform apply -auto-approve -input=false \
  -var="image=<image-ref>" \
  -var="workload_identity_pool_name=projects/1039475970204/locations/global/workloadIdentityPools/vis-k2-github-pool" \
  -target=module.network -target=module.iam -target=module.secret_manager -target=module.cloud_sql
```

Then populate every secret (see step 3), then:

```bash
# Stage 2: the rest
terraform apply -auto-approve -input=false \
  -var="image=<image-ref>" \
  -var="workload_identity_pool_name=projects/1039475970204/locations/global/workloadIdentityPools/vis-k2-github-pool" \
  -target=module.redis -target=module.cloud_run_service
# (cloud_run_job / cloud_scheduler / monitoring not needed just to test the app manually —
#  only needed for the nightly bulk jobs and alerting)
```

`terraform/modules/secret-manager` only ever creates the secret **containers** — it never writes a value (see the module's own header comment). Values are always populated out-of-band via `gcloud secrets versions add`, never through Terraform state.

#### Step 3. Populate every secret — including the two you want to leave "disabled"

```bash
printf '%s' "$FMP_API_KEY" | gcloud secrets versions add vis-k2-staging-fmp-api-key --data-file=-
```

**Real bug hit and worked around:** the module's own comment claims leaving `google-client-id`/`google-client-secret` with **no version at all** "keeps Google sign-in disabled for that environment". That's **false** given how Cloud Run + Secret Manager actually behave together: (a) Secret Manager refuses an empty payload outright, and (b) even if it didn't, Cloud Run's secret env binding needs *some* version of *every* referenced secret to exist, or the whole service fails to create — not just that one env var. Push a placeholder value (`"disabled-not-configured"`) to both, which means Google Sign-In is **not actually disabled** — `GoogleOAuthConfig`'s activation check is `!isEmpty()`, so any non-blank string activates it, just with an invalid client (Google itself then rejects real sign-in attempts with `401 invalid_client` until real credentials are pushed — see step 6). **This is a real IaC gap worth fixing properly** (e.g. the module should either not declare those env vars unless real values are supplied, or the app should tolerate a documented sentinel value) — not yet fixed, only worked around live each time.

**JWT keys — format gotcha:** `openssl genrsa -out key.pem 2048` produces a **PKCS#1** key (`-----BEGIN RSA PRIVATE KEY-----`). `JwtService.parsePrivateKey` only strips the **PKCS#8** markers (`-----BEGIN PRIVATE KEY-----`) and feeds the result straight into `PKCS8EncodedKeySpec` — a PKCS#1 key silently fails to parse and crashes the whole Spring context at startup (`Constructor threw exception` on `JwtService`, surfaces as a failed Cloud Run startup probe with no obviously-JWT-related message in the logs). Generate the key correctly instead:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt_private.pem   # PKCS#8, correct
openssl rsa -in jwt_private.pem -pubout -out jwt_public.pem
```

Database credentials: `terraform/modules/cloud-sql` creates the **instance** and the **`vis` database only** — no app user. Create one yourself and push matching secret values:

```bash
gcloud sql users create vis_app --instance=vis-k2-staging-postgres --password="$DB_PASS"
printf '%s' "vis_app" | gcloud secrets versions add vis-k2-staging-database-username --data-file=-
printf '%s' "$DB_PASS" | gcloud secrets versions add vis-k2-staging-database-password --data-file=-
```
(`gcloud sql users create` is an Admin-API call — it does **not** need network access to the private-IP instance, unlike everything in step 5.)

#### Step 4. Force a new Cloud Run revision after changing a secret's value

Cloud Run does **not** pick up a new "latest" secret version on an already-running revision. After updating any secret with `gcloud secrets versions add`, force a fresh revision:

```bash
gcloud run services update vis-k2-staging-api --region=europe-west1 \
  --update-labels=redeployed-at=$(date +%s)
```
(a harmless label bump is enough to force a new revision without touching the image or any other config)

#### Step 5. Bootstrapping the first ADMIN user (no self-registration in `prod` profile)

There is no `/auth/register` endpoint and no seed-admin `CommandLineRunner` gated for the `prod` profile (`RealDemoUserSeeder` exists but is `@Profile("realDemo")` only — irrelevant here). The Cloud SQL instance is **private-IP only**, and a laptop typically has no VPC route to it, so the only way in is a temporary, explicit, reversible public-IP window:

```bash
MY_IP=$(curl -s https://ifconfig.me)
gcloud sql instances patch vis-k2-staging-postgres --assign-ip --authorized-networks="$MY_IP/32" --quiet
```

Then run `cloud-sql-proxy` locally (download the platform-appropriate binary from `storage.googleapis.com/cloud-sql-connectors/...` — no install needed) and connect via a throwaway `postgres:16-alpine` Docker container (`host.docker.internal` reaches the proxy on the host machine):

```bash
./cloud-sql-proxy vis-version0:europe-west1:vis-k2-staging-postgres --port 5432 &
docker run --rm -e PGPASSWORD="$DB_PASS" postgres:16-alpine \
  psql -h host.docker.internal -p 5432 -U vis_app -d vis -c \
  "INSERT INTO app_user (id, email, password_hash, role, active, created_at) VALUES (gen_random_uuid(), '<email>', '<bcrypt-hash>', 'ADMIN', true, now());"
```

Generate the bcrypt hash with `htpasswd -nbB <email> '<password>'` (Spring's `BCryptPasswordEncoder` accepts the `$2y$` prefix `htpasswd` produces — jBCrypt treats 2a/2b/2y as equivalent for verification). **Revert the public IP immediately after**, this is not meant to be a standing configuration:

```bash
gcloud sql instances patch vis-k2-staging-postgres --no-assign-ip --quiet
```

**Never** print the DB password or the bcrypt hash into a committed file, the vault, or persistent logs — keep them only in a local scratch file/env var and in the single `psql` command's own arguments for the INSERT, per the "never place secret values" rule at the bottom of this section.

#### Step 6. Reaching the app — two options, pick based on what you're testing

- **Private, authenticated-tunnel access** (no public exposure at all): `gcloud run services proxy vis-k2-staging-api --region=europe-west1 --port=8888`, then browse `http://127.0.0.1:8888`. Good for API-level smoke testing (seed + generate thesis via curl). **Does not work for testing Google Sign-In from a real browser session** in a way a human would recognize as "the real login flow" — the proxy authenticates *you* to Cloud Run's IAM layer transparently, which is fine for API calls but adds a layer a real end-user would never have.
- **Public** (`allUsers` → `roles/run.invoker`), for testing exactly like a real user would, including Google Sign-In:
  ```bash
  gcloud run services add-iam-policy-binding vis-k2-staging-api --region=europe-west1 \
    --member="allUsers" --role="roles/run.invoker"
  ```
  This is commonly blocked by an agent's own sandbox/approval layer even with a matching allow rule — ask for explicit confirmation first. **Revoke this when done testing** — the app itself still gates real functionality behind login, but Cloud Run's own IAM layer is the outer perimeter and shouldn't be left open indefinitely:
  ```bash
  gcloud run services remove-iam-policy-binding vis-k2-staging-api --region=europe-west1 \
    --member="allUsers" --role="roles/run.invoker"
  ```

#### Step 7. Real Google Sign-In (not just the placeholder credential from step 3)

Creating an actual OAuth 2.0 Client ID is **Console-only** — Google has never exposed a public API/`gcloud` command for it (`gcloud iap oauth-brands` is for IAP, a different, unrelated OAuth surface). A human has to:

1. [Console → APIs & Services → Credentials → Create Credentials → OAuth client ID](https://console.cloud.google.com/apis/credentials?project=vis-version0)
2. Type **Web application** (not Desktop — Desktop has no configurable redirect URI at all, `redirect_uri_mismatch`, this exact mistake was made once already on `dev`, 2026-08-27)
3. Authorized redirect URI, exactly: `https://vis-k2-staging-api-<hash>-ew.a.run.app/login/oauth2/code/google`
4. Confirm the tester's own email is listed under **OAuth consent screen → Audience → Test users** (consent screen stays in **Testing** status — avoids Google's verification review while still gating sign-in to named accounts)
5. Copy Client ID + Secret back to the agent, which pushes them into `vis-k2-staging-google-client-id`/`-google-client-secret` and forces a new revision (step 4 above)

**Note on the Cloud Run URL hash**: `terraform/environments/staging/main.tf` hardcodes the redirect URL using a hash suffix (`ughjapmueq` as of the 2026-09-03 recreate) that has stayed stable across at least one full destroy/recreate cycle, matching what `dev` also observed — the comment in that file warns to "re-check this value after any destroy/recreate" but in practice it turned out to be the *actual, correct* value again. Cloud Run v2's hash-based URL format appears to be derived from something project/region-invariant (not the full service name), not freshly randomized per create as the comment implies. Confirmed live at least once, not just assumed — worth a closer look before trusting this comment's warning at face value next time, and re-verify if it ever does change.

#### Known state as of the last update to this file (2026-09-03) — historical record, re-check with step 1 before trusting it

- Staging was **torn down** on 2026-09-03 after a live test session (see teardown notes below). If you're reading this later, do not assume this state still holds — always run the step 1 health checks first.
- Last live URL before teardown: `https://vis-k2-staging-api-ughjapmueq-ew.a.run.app`.
- Applied at that time: network, iam, secret_manager, cloud_sql, redis, cloud_run_service. **Not applied**: cloud_run_job, cloud_scheduler, monitoring (not needed for manual/API testing, would be needed before relying on nightly bulk jobs or alerting on this environment).
- Real Google OAuth client was configured (client ID/secret pushed to Secret Manager, sourced from `specs/oauth2.md`, which is gitignored — never copy OAuth secret values into this file or the vault).
- Both a REIT ticker and a non-REIT ticker were seeded and had a real AI thesis generated via the live Vertex AI Gemini endpoint, confirming RM4 (REIT-specific evidence fields) and TA6 (corrected trend direction) both work in this deployment, not just in unit tests.
- Known unresolved oddity: `GET /actuator/health` (root, aggregating all indicators) returned `503 DOWN` even though both the `liveness` and `readiness` groups individually reported `UP`. Not investigated further — didn't block any functional testing.

#### Teardown notes (2026-09-03) — read if a `terraform destroy` on this environment gets stuck

`terraform destroy -var="image=..." -var="workload_identity_pool_name=..."` for `environments/staging`, using the same local bootstrap credentials as the provisioning above (CI can't run this either — its own deployer SA gets destroyed by this exact teardown too).

**Gotcha found on this teardown:** `terraform destroy` (including automatic retries) can fail on two resources:
1. `google_service_networking_connection.private_service_connection` — `"Producer services (e.g. CloudSQL, Cloud Memstore, etc.) are still using this connection"` (propagation-lag error, known from K1/K2's own history — retrying after a short wait usually clears it).
2. `google_compute_subnetwork.this` — `"already being used by '.../addresses/serverless-ipv4-<id>', resourceInUseByAnotherResource"`.

Root cause of (2): Cloud Run's **Direct VPC Egress** reserves an internal `SERVERLESS`-purpose IP address on the subnet as a side effect of `google_cloud_run_v2_service` — Terraform never creates or tracks this address as a resource, so `terraform destroy` has no way to release it itself, and it blocks subnet deletion until Google's own `serverless.googleapis.com` address-reservation garbage collection releases it (`gcloud compute addresses delete <name>` fails too, for the same "already being used by .../addressReservations/..." reason — this cannot be force-deleted, only waited out).

To confirm nothing billable remains despite this, sweep the whole project: `gcloud sql/redis/run services/run jobs/secrets (vis-k2-staging-*)/iam service-accounts (vis-k2-staging-*)/scheduler jobs` should all come back empty.

**Non-billable residue pending Google's own release** (matches K1/K2's documented pattern):
- VPC network `vis-k2-staging-network`
- Subnet `vis-k2-staging-subnet`
- Reserved address `vis-k2-staging-managed-services` (private service connection range)
- Reserved address `serverless-ipv4-<id>` (Cloud Run direct VPC egress range)

**To finish the destroy once Google releases the above** (check first with `gcloud compute addresses list --project=vis-version0 --filter="name:vis-k2-staging* OR name:serverless-ipv4*"` — once empty or no longer `RESERVED`, retry):
```bash
export PATH="$HOME/bin:$PATH"
cd terraform/environments/staging
terraform destroy -auto-approve -input=false \
  -var="image=<any value, unused for this leftover resource set>" \
  -var="workload_identity_pool_name=projects/1039475970204/locations/global/workloadIdentityPools/vis-k2-github-pool"
```
Not urgent — none of the four leftover resources carries any cost.

**Local cleanup after any teardown**: `docker compose stop postgres redis` (local test containers, not deleted, zero cost while stopped), delete scratch JWT/DB-credential files, kill any lingering `spring-boot:run`/`cloud-sql-proxy`/`gcloud run services proxy` processes.

#### Rule carried over from K1's own note

Never place secret values (passwords, private keys, OAuth client secrets, bcrypt hashes) in this file, the Obsidian vault, the repository, shell output, logs, or command arguments any longer than the single command that needs them.
