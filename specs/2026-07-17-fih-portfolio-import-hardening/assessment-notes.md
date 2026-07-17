# FIH1 — Assessment Notes (2026-07-17)

## Findings re-verified against current `main`

Both findings reproduce exactly as described in `Code Review 2026-07-16`, at these current locations in `backend/src/main/java/it/mazzoni/vis/portfolio/importing/PortfolioImportService.java`:

1. **Cost-basis silent data loss (Quality, Critical)** — `synchronizeHolding` (line 199 as of this commit):
   ```java
   holding.setAverageCostBasis(null); holding.setCurrency(...);
   ```
   Called unconditionally for both newly-created and pre-existing holdings. Confirmed.

2. **Unauthorized shared-data ISIN binding (Security, Warning)** — `applyMappings` (lines 176–191):
   ```java
   target.setIsin(row.getIsin()); securities.save(target);
   ```
   No role or ownership check anywhere in the method. `Security.setIsin` is still called from exactly this one place in the codebase (`grep -rn "\.setIsin("` confirms). Confirmed.

## Pre-existing corrupted data check

No live production or shared demo deployment exists yet for this platform (per `specs/mission.md`'s K1/K2/K3 cloud distribution phases, all still pending). The only database state in this environment is local/ephemeral Testcontainers/H2 instances used for tests, which are torn down after each run — there is no persistent PostgreSQL instance with real imported-portfolio data to inspect. `docker ps` at the time of this assessment shows only a local Redis container running (left over from FI2/FI3 re-verification), no PostgreSQL.

**Conclusion: no data-correction step is needed.** Both fixes in this phase are pure code changes; no backfill or one-time data note applies. This will need re-checking before any real production rollout that already ran the buggy code — flagged here for that future point in time, not actioned now.

## Scope confirmed

Proceeding with `plan.md` sections 2–6 (cost-basis fix, ISIN admin-approval gate + new admin endpoint, test coverage, N+1 fixes, full-suite hard-gate verification) as scoped in `requirements.md`. Section 7 (FIH5 optional cleanup) will only be attempted if it falls out naturally while writing section 4's tests.
