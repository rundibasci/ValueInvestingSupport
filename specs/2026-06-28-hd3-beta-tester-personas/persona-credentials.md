# HD3 Persona Credentials

These are local demo credentials created for the Docker localstack run. They are not production accounts.

| Persona | Email | Password | Role |
|---|---|---|---|
| Very prudent value investor | `prudent.beta@localstack.local` | `PersonaDemo123!` | `INVESTOR` |
| Hedge-fund asset allocator | `allocator.beta@localstack.local` | `PersonaDemo123!` | `ADVISOR` |
| Financial journalist / trend observer | `journalist.beta@localstack.local` | `PersonaDemo123!` | `INVESTOR` |

Admin account used for setup:

| Email | Password | Role |
|---|---|---|
| `admin@localstack.local` | `admin` | `ADMIN` |

## Database Evidence

The persona users are present in the persisted HD3 database dump:

```text
allocator.beta@localstack.local  ADVISOR
journalist.beta@localstack.local INVESTOR
prudent.beta@localstack.local    INVESTOR
```

Persisted database file:

```text
specs/2026-06-28-hd3-beta-tester-personas/hd3-beta-personas-demo.pgcustom
```
