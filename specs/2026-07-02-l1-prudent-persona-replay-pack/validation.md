# L1 Validation - Prudent Persona Replay Pack

## Acceptance Checks

- `plan.md`, `requirements.md`, and `validation.md` exist and are non-empty.
- The replay runner has dry-run mode and writes a manifest under the L1 evidence folder.
- In live mode, the replay attempts seed, review packet capture, equal-weight portfolio concentration checks, oversized concentration checks, and watchlist rationale persistence checks.
- Persisted evidence redacts access and refresh tokens.
- The evidence summary includes the decision-support boundary.
- No Group K/K1/K2/K3 files or infrastructure are changed.

## Validation Commands

Run from the repository root:

```powershell
Test-Path specs/2026-07-02-l1-prudent-persona-replay-pack/plan.md
Test-Path specs/2026-07-02-l1-prudent-persona-replay-pack/requirements.md
Test-Path specs/2026-07-02-l1-prudent-persona-replay-pack/validation.md
powershell -ExecutionPolicy Bypass -File scripts/l1-prudent-persona-replay.ps1 -SkipLiveApi
cd backend; .\mvnw.cmd test
```

## Manual QA

With the local stack running and seeded credentials available:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/l1-prudent-persona-replay.ps1 -BaseUrl http://localhost:8080
```

Review `specs/2026-07-02-l1-prudent-persona-replay-pack/evidence/replay-summary.md` and confirm:

- Every Agent 1 symbol has a review artifact or an explicit error artifact.
- The equal-weight portfolio result reports no holding concentration breach when data is available.
- The oversized KO or JNJ scenario reports at least one concentration warning when portfolio detail is available.
- PG, KO, JNJ, and MSFT rationale notes are present after watchlist reload.

## Merge Readiness

- Spec files are non-empty.
- Dry-run replay passes.
- Backend tests pass.
- Git diff is limited to L1 spec/evidence files, the replay runner, changelog, and vault activity documentation.

## Known Risks

- Live market-data providers can return partial, stale, or rate-limited data. The replay records those states as data-quality evidence.
- Existing local credentials may differ by profile. The runner exposes credential parameters for live use.
- Some endpoints may reject duplicate portfolio or watchlist entries on repeated live runs. The runner records those failures explicitly instead of masking them.
