# HD3 Decisions Log

## Feature Decisions

- Phase selected: HD3 - Beta Tester Persona Simulation.
- Personas may modify the shared seeded universe to simulate real investor behavior.
- Validation requires a full Docker local demo run.
- Reports use deterministic localstack data plus curated source summaries, not paywalled scraping.
- Persona outputs are beta-test artifacts and must not be presented as investment advice.

## Runtime Decisions

- Docker command used: `docker compose up -d --build`.
- Backend health endpoint reached `UP` with PostgreSQL and Redis.
- Frontend route returned `HTTP 200` at `http://127.0.0.1:5173`.
- Three persona accounts were created through the admin API.
- Persona workflow state was persisted in PostgreSQL and exported with container `pg_dump`.

## Persona Account Decisions

- `prudent.beta@localstack.local` uses role `INVESTOR`.
- `allocator.beta@localstack.local` uses role `ADVISOR`.
- `journalist.beta@localstack.local` uses role `INVESTOR`.
- All persona accounts use the local demo password `PersonaDemo123!`.

## Evidence Decisions

- API evidence is treated as primary because it directly records database state.
- Browser automation through the in-app connector was attempted but failed to initialize due missing runtime sandbox metadata. This is documented as a validation limitation.
- No screenshots were committed because the browser connector did not initialize.
- The database dump is committed as `hd3-beta-personas-demo.pgcustom` so HD4 can inspect the exact demo state.

## Discovered Defect Decision

- Seed transaction failure was fixed during HD3 because it blocked realistic persona reseeding.
- Fix: add `@Transactional` to `SeedService.seedTickers`.
- This is a tightly scoped supporting fix and should be mentioned in changelog/merge notes.
