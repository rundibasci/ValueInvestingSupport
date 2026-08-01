# TRAIN-01 — Validation

## Acceptance Criteria

- [x] Esiste `vis-model-training/README.md` e descrive TRAIN come fase parallela a VIS.
- [x] Esistono entrambi i JSON Schema nei percorsi richiesti.
- [x] Esiste `prompts/system-prompt-v1.txt` con le dodici regole concordate.
- [x] Esistono i tre esempi manuali sintetici concordati.
- [x] `seed-dataset-v1.jsonl` contiene esattamente tre righe JSON non vuote e nessun array esterno.
- [x] Ogni documento, messaggio incorporato e riga JSONL è JSON sintatticamente valido.
- [x] Ogni input incorporato rispetta `thesis-input.schema.json`.
- [x] Ogni output incorporato rispetta `thesis-output.schema.json`.
- [x] Ogni `evidenceFields` contiene soltanto campi ammessi e presenti nell'input.
- [x] Gli esempi rappresentano un caso positivo, una potenziale value trap e dati insufficienti.
- [x] Gli esempi non contengono ordini o raccomandazioni buy/sell/hold, dati esterni o valori inventati.
- [x] `humanReviewRequired` è `true` nei casi dubbi o insufficienti.
- [x] TRAIN-01 non richiede modello, librerie AI, Docker di training o GPU.

## Dataset Test Matrix

| Scenario | Expected result |
|---|---|
| `example-001.json` | Valido; `POTENTIALLY_UNDERVALUED`; evidenze positive; revisione umana non richiesta |
| `example-002.json` | Valido; `UNDER_REVIEW`; value-trap e warning deterministici; revisione umana richiesta |
| `example-003.json` | Valido; `INSUFFICIENT_DATA`; indicatori null/indisponibili; revisione umana richiesta |
| Campo input aggiuntivo | Rifiutato da `additionalProperties: false` |
| Classificazione non ammessa | Rifiutata dallo schema output |
| Confidence fuori da 0–1 | Rifiutata dallo schema output |
| Evidence field non ammesso o assente | Rifiutato dal controllo del contratto/evidenze |
| JSON incorporato malformato | Parsing fallito e record non accettato |
| JSONL racchiuso in array | Non accettato come seed dataset TRAIN-01 |
| Dataset con numero righe diverso da tre | Acceptance check fallito |

## Regression Checks

- [x] Nessun file in `backend/`, `frontend/` o nelle configurazioni Compose VIS è modificato.
- [x] Nessuna dipendenza AI è aggiunta al progetto.
- [x] Nessun token, segreto, peso modello, cache, checkpoint o adapter è tracciato.
- [x] I file richiesti sono tutti sotto `vis-model-training/`.
- [x] `git diff --check` passa.

## Verification Commands

```bash
# JSON syntax for schemas and examples
jq empty vis-model-training/schemas/*.json
jq empty vis-model-training/examples/*.json

# Contract, evidence, scenarios and examples/JSONL identity
node vis-model-training/scripts/validate-dataset.mjs

# Exactly three valid, non-empty JSONL records
test "$(grep -cve '^[[:space:]]*$' vis-model-training/datasets/seed-dataset-v1.jsonl)" -eq 3
jq -c . vis-model-training/datasets/seed-dataset-v1.jsonl >/dev/null

# Embedded user/assistant payload syntax
jq -r '.messages[] | select(.role == "user" or .role == "assistant") | .content' \
  vis-model-training/examples/*.json | jq empty

# Repository hygiene
git diff --check
git status --short
```

La conformità Draft 2020-12, i riferimenti delle evidenze e la corrispondenza esempi/JSONL devono inoltre essere verificati con uno script leggero o con un validatore già disponibile durante l'implementazione, senza installare lo stack AI.

## Manual Validation

1. Aprire e analizzare tutti i JSON senza errori sintattici.
2. Per ogni esempio, decodificare `user.content` e `assistant.content`.
3. Confrontare input e output con i rispettivi schemi.
4. Confrontare ogni claim con i campi elencati in `evidenceFields`.
5. Confermare coerenza fra scenario, classificazione, confidence e revisione umana.
6. Cercare indicazioni buy/sell/hold e informazioni esterne sull'azienda; il risultato deve essere vuoto.
7. Confermare che non siano stati eseguiti download, setup AI o accessi GPU.

## Merge Gate

TRAIN-01 è merge-ready quando tutti gli artefatti richiesti esistono, il JSONL contiene esattamente i tre esempi concordati, sintassi e contratti sono validi, le evidenze sono tracciabili, i tre scenari sono coperti e nessun software di training o cambiamento runtime VIS è stato introdotto.
