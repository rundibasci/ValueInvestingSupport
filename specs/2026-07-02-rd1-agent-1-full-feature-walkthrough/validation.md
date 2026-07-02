# RD1-2 Validation - Agent 1 Full Feature Walkthrough & Screenshots

## Acceptance Checks

- `plan.md`, `requirements.md`, and `validation.md` exist and are non-empty.
- The replay runner documents and exercises the Agent 1 real-demo workflow without requiring secrets.
- The walkthrough report lists each roadmap-required workflow, evidence route, expected result, and pass/fail status.
- The screenshot manifest names each expected screenshot and redaction requirement.
- No Group K/K1/K2/K3 files or infrastructure are changed.
- No committed artifact contains provider secrets, JWTs, refresh tokens, personal data, or raw provider redistribution payloads.

## Validation Commands

Run from the repository root unless noted:

```powershell
Test-Path specs/2026-07-02-rd1-agent-1-full-feature-walkthrough/plan.md
Test-Path specs/2026-07-02-rd1-agent-1-full-feature-walkthrough/requirements.md
Test-Path specs/2026-07-02-rd1-agent-1-full-feature-walkthrough/validation.md
powershell -ExecutionPolicy Bypass -File scripts/rd1-agent1-walkthrough.ps1 -SkipLiveApi
cd frontend; npm run build
cd backend; .\mvnw.cmd test
```

## Manual QA

With the real-demo stack running:

```powershell
docker compose -f docker-compose.realDemo.yml up --build
powershell -ExecutionPolicy Bypass -File scripts/rd1-agent1-walkthrough.ps1
```

Then open `http://localhost:5173`, follow the screenshot manifest, and save screenshots into the phase `screenshots/` folder using the specified filenames.

## Merge Readiness

- Backend tests pass.
- Frontend build passes.
- Replay runner dry-run mode passes.
- Git diff is limited to RD1-2 spec/evidence files, the walkthrough runner, changelog, and vault activity documentation.

## Known Risks

- Live Yahoo Finance availability can make real-demo evidence variable. The runner records per-step errors instead of hiding provider limitations.
- Screenshot capture depends on a running local browser and real-demo stack; the manifest keeps this repeatable even when full browser automation is unavailable.
