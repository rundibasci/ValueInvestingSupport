# Persona Replay - Phase I1

## Agent 1 Prudent-Value Replay

Script:

```powershell
./scripts/i1-persona-replay.ps1 -BaseUrl http://localhost:8080
```

Purpose:

- Replay the HD3/HD4 prudent-value review loop against a local backend.
- Check the 10-symbol validation set: `BRK.B`, `JNJ`, `PG`, `KO`, `PEP`, `WMT`, `MSFT`, `ADP`, `UNP`, `XOM`.
- Capture score availability, valuation availability, quote availability, margin of safety, recommendation, and data-quality notes.

Required local state:

- Backend running locally.
- Persona user `prudent.beta@localstack.local` present with the local demo password.
- Symbols seeded with deterministic local/demo data.

Boundary:

- This replay produces workflow evidence only.
- It must not be described as an investable model portfolio or personalized investment advice.

## Deferred Persona Automation

The allocator and journalist personas remain documented HD3 workflows. I1 treats them as follow-up automation candidates unless the implementation phase adds enough deterministic fixture data to replay them without a live provider or manual setup.
