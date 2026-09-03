Reactivate or refresh the K2 `staging` GCP environment (`vis-version0`, `europe-west1`, resource prefix `vis-k2-staging`) for manual testing — this repo's staging CI/CD (`k2-deploy-staging.yml`) requires a deployer service account that has been destroyed by a prior teardown more than once, so this is done locally instead.

**Canonical runbook — read this first, every time:** `~/Documents/valueinvestorsupport/ValueInvestingSupport/2026-09-03-K2 - Staging manual bootstrap runbook.md` (Obsidian vault; a plain file on disk, readable directly). It has the exact commands, every gotcha hit so far (image must be `--platform linux/amd64`, JWT key must be PKCS#8 not PKCS#1, Secret Manager containers referenced by Cloud Run need *some* version even when "meant to stay disabled," no self-registration endpoint so the first ADMIN user needs a temporary public-IP bootstrap window, the two ways to reach the app), and the state as of its last update. Read it in full before doing anything — do not re-derive the procedure from memory or from this file alone, this file is a trigger + state-check protocol, not a replacement for the runbook's detail. **If you discover a new gotcha or the procedure changes, update that note afterward** — it's the single source of truth this command exists to keep current.

## 1. Check current state before touching anything

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

- **A. Fully torn down** (no service accounts, no Cloud Run, no Cloud SQL) → full bootstrap, runbook §1-7 in order.
- **B. Up, but stale code** (Cloud Run healthy, but its deployed image tag ≠ current `git rev-parse HEAD`) → skip straight to runbook §1 (build+push, matching this session's HEAD) and §4 (force a new revision via `terraform apply -target=module.cloud_run_service`), skip §2/§3/§5 entirely — the environment, secrets, and admin user already exist.
- **C. Up and current** → nothing to reactivate. Just run the health checks above, report the URL, and stop — do not touch Terraform, secrets, or the database. If asked "why isn't X working" when the service itself is healthy, check Cloud Run request logs for the actual failing path/status first (`gcloud logging read 'resource.type="cloud_run_revision" AND resource.labels.service_name="vis-k2-staging-api" AND (httpRequest.status>=400 OR severity>=ERROR)' --project=vis-version0 --freshness=15m`) before assuming an infra problem — a session-expired JWT refresh loop or similar app-level issue looks like "not responding" from the outside but isn't a reactivation task.

## 2. Confirm before spending

State A (full bootstrap) provisions real billable resources (Cloud SQL, Memorystore Redis, Cloud Run) — confirm with the user before starting unless they already explicitly asked for this specific reactivation in the current conversation. State B (redeploy only) is near-zero incremental cost (rebuilding an image and rolling a new revision of already-running infra) — proceed without asking again if the user's request to reactivate/redeploy staging is what triggered this command.

## 3. Known one-off approvals this session will likely need

A few individual actions get blocked by Claude Code's own auto-mode classifier even with a matching `Bash` allow rule already in `.claude/settings.json` (`terraform *`, `gcloud sql *` are already allowed as of 2026-09-03) — expect to ask the user to run these themselves, or to get an explicit one-off approval, rather than treating a block as fatal:
- `gcloud sql instances patch ... --assign-ip` (temporary public IP for the admin-bootstrap step)
- `gcloud run services add-iam-policy-binding ... --member=allUsers` (making Cloud Run publicly reachable, only needed if the user wants to test from a real browser instead of `gcloud run services proxy`)

Editing `.claude/settings.json` to add a new permission rule is also blocked when Claude tries it itself (self-granting is a hard boundary) — if a genuinely new class of command needs approval, ask the user to add the rule by hand (tell them exactly what line to add) rather than repeatedly retrying the same blocked command.

## 4. After reactivating

Report back: the URL, which state (A/B/C) applied, whether the admin user / Google OAuth credentials still work (state B/C should not need to touch either), current Cloud Run IAM mode (private-proxy-only vs. public), and remind the user staging has a real hourly cost while left up — ask whether to tear it down when they're done testing, don't do it unprompted.
