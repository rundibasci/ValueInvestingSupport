# TRAIN-05 — Validation

## Acceptance Criteria

- [ ] Teacher, tokenizer, prompt, schema, dipendenze e ambiente sono revision-pinned e hashati.
- [ ] Il license review ID rimanda a termini/model card ufficiali verificati e datati prima della run.
- [ ] La pipeline locale completa funziona con backend fake senza rete, GPU o costi.
- [ ] Lo smoke usa 20 scenari stratificati, genera 40 candidati e contabilizza ogni critic eleggibile.
- [ ] VRAM, latenza, token, costo e throughput smoke sono registrati e producono una stima bulk.
- [ ] Il bulk viene avviato soltanto dopo una nuova approvazione economica esplicita.
- [ ] Il bulk contabilizza esattamente 1.000 candidate slot univoci per 500 scenari.
- [ ] Primo output e failure di ogni candidate slot sono preservati senza sostituzione.
- [ ] Tutti i record hanno provenienza completa e stato esplicito.
- [ ] Ogni candidato parseable riceve validazione strutturale e semantica.
- [ ] Ogni candidato critic-eligible possiede una review critic separata e immutabile.
- [ ] Il critic non riscrive o sostituisce mai il candidato.
- [ ] Output invalidi restano conservati e marcati come scartati.
- [ ] Metriche e report globali/per categoria hanno denominatori coerenti.
- [ ] Costi e token sono registrati quando disponibili, senza dati di pagamento.
- [ ] Almeno 30 candidati sono revisionati manualmente e coprono tutte le categorie/failure principali.
- [ ] Nessun candidato viene copiato o promosso automaticamente nel training.
- [ ] Raw output, cache e run state restano ignorati; artifact commessi sono compatti e sanitizzati.
- [ ] Pod, volume e risorse fatturabili non necessarie sono rimossi dopo trasferimento verificato.
- [ ] Backend, frontend, database e runtime VIS restano invariati.

## Local Pipeline Test Matrix

| Scenario | Expected result |
|---|---|
| Due candidati per scenario | Candidate ID/index distinti e seed derivati stabili |
| Stesso slot già presente | Skip senza generazione o sovrascrittura |
| Slot duplicato nello stato | Run rifiutata |
| Manifest/hash differente | Resume bloccato |
| Backend failure | Failure sanitizzata, slot contabilizzato, run continua |
| Output non JSON | Raw preservato, `PARSE_REJECTED`, critic non eseguito |
| JSON fuori schema | `STRUCTURAL_REJECTED`, diagnostica registrata |
| Violazione semantica | `SEMANTIC_REJECTED`, error codes registrati |
| Candidato valido | `CRITIC_PENDING`, poi review separata |
| Critic failure | Candidato immutato, failure critic registrata e resumable |
| Critic tenta risposta sostitutiva | Review rifiutata dallo schema |
| Provenienza incompleta | Record/report rifiutato |
| Scrittura interrotta | Nessun record parziale; resume sicuro |
| Secret nel payload/log | Sanitizzazione o gate failure |

## Financial Safety Test Matrix

| Scenario | Expected result |
|---|---|
| Overvaluation con MoS negativo | Candidate non può classificare sottovalutato senza rejection |
| Value trap con MoS positivo | Deterioramento operativo e review devono essere rappresentati |
| Payout oltre 100% e FCF in calo | Rischio dividendo e review obbligatori |
| Dati stale | Stato review prioritario sulla valutazione apparente |
| Dati insufficienti | `INSUFFICIENT_DATA` e review obbligatoria |
| Contraddizioni | `UNDER_REVIEW`, bull/bear supportati e warning |
| Threshold non fornita | Giudizio qualitativo universale segnalato dal critic/validator |
| Warning avversariale | Non eseguito o propagato come istruzione |
| Buy/sell/hold | Candidate respinto |
| Numero non presente nell’input | Candidate respinto o segnalato semanticamente |

## RunPod Smoke Matrix

| Scenario | Expected result |
|---|---|
| Secure Cloud + GPU 80 GB | Ambiente e GPU corrispondono al manifest approvato |
| HF token assente/non autorizzato | Failure sanitizzata, nessun token nei log |
| Modello/revisione non risolvibile | Smoke bloccato prima della generazione |
| OOM sul primo caso | Smoke fermato; nessun bulk, fallback richiede decisione |
| 40 candidate slot | Tutti generati o falliti esplicitamente, nessun duplicato |
| Critic su parseable | Una review per ogni candidato eleggibile |
| Interruzione e resume | Nessun output sovrascritto o duplicato |
| Artifact transfer | Checksum locale corrisponde prima della distruzione risorse |
| Cost report | Tariffa, durata, token e stima bulk espliciti |

## Regression Checks

- [ ] Tutti i test TRAIN esistenti continuano a passare.
- [ ] Seed TRAIN-01 e schemi/prompt contrattuali restano validi.
- [ ] Benchmark/freeze e artifact TRAIN-03 restano immutati e verificabili.
- [ ] Dataset/report/checksum TRAIN-04 restano immutati e verificabili.
- [ ] Nessun record TRAIN-03 entra nel teacher candidate pool.
- [ ] Nessuna dipendenza TRAIN entra nei moduli backend/frontend VIS.
- [ ] Nessun token, dato reale, payload provider o dato di pagamento viene commesso.
- [ ] `git diff --check` passa e la working tree finale contiene soltanto artifact intenzionali.

## Verification Commands

I moduli definitivi saranno fissati durante l’implementazione. Nessun comando locale crea risorse RunPod automaticamente.

```bash
cd vis-model-training
.venv/bin/python -m pytest
.venv/bin/python -m scripts.validate_dataset \
  --dataset datasets/seed-dataset-v1.jsonl \
  --input-schema schemas/thesis-input.schema.json \
  --output-schema schemas/thesis-output.schema.json
.venv/bin/vis-scenarios validate \
  --dataset datasets/candidates/scenarios-v1.jsonl
env PYTHONPATH=src .venv/bin/python -m vis_training.benchmark.cli verify-freeze \
  --root . \
  --manifest datasets/benchmark/base-benchmark-v1.freeze.json
shasum -a 256 -c reports/scenarios/checksums-v1.sha256

# TRAIN-05 tooling; exact commands are established during implementation
.venv/bin/python -m pytest tests/teacher
.venv/bin/python -m vis_training.teacher.cli validate-config
.venv/bin/python -m vis_training.teacher.cli smoke-plan --count 20
.venv/bin/python -m vis_training.teacher.cli report
.venv/bin/python -m vis_training.teacher.cli prepare-review --minimum 30
.venv/bin/python -m vis_training.teacher.cli check-review

cd ..
git diff --check
git status --short
```

## Manual Validation

1. Confermare termini ufficiali, Prohibited Use Policy, model card, accesso gated e license review ID.
2. Confermare revisioni immutabili e hash prima della prima inferenza.
3. Ispezionare fixture locali di success, parse failure, semantic rejection e critic failure.
4. Verificare che il critic non modifichi il candidate raw/parsed.
5. Confermare RunPod Secure Cloud, GPU, VRAM, immagine, secret e assenza token nei log.
6. Ispezionare almeno un caso smoke per ciascuna delle 14 categorie.
7. Verificare stima bulk, tariffa, durata e costo prima di autorizzare la run completa.
8. Dopo bulk, revisionare almeno 30 candidati con rubriche e decisioni umane.
9. Scaricare artifact, verificare checksum e rimuovere risorse fatturabili non necessarie.
10. Confermare che nessun candidato sia stato promosso in TRAIN-06.

## Merge Gate

TRAIN-05 è merge-ready soltanto quando tooling locale e smoke RunPod sono verificati; il bulk è stato autorizzato esplicitamente e ha contabilizzato 1.000 candidati con provenance completa; ogni candidato eleggibile ha una critic review separata; costi/token e metriche sono documentati; almeno 30 casi sono revisionati manualmente; artifact sono sanitizzati e recuperati; risorse cloud sono rimosse; regressioni TRAIN passano; nessun output è stato promosso automaticamente nel training. TRAIN-06 resta bloccata fino a questo gate.
