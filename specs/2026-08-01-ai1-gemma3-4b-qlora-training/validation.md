# TRAIN-01 — Validation

## Acceptance Criteria

- [x] Esiste `vis-model-training/README.md` e descrive TRAIN come fase parallela a VIS.
- [x] Esistono entrambi i JSON Schema nei percorsi richiesti.
- [x] Esiste `prompts/system-prompt-v1.txt` con le dodici regole concordate.
- [x] Esistono tutti i dieci esempi manuali sintetici richiesti.
- [x] `seed-dataset-v1.jsonl` contiene esattamente dieci righe JSON non vuote e nessun array esterno.
- [x] Tutti i dieci documenti, i messaggi incorporati e le righe JSONL sono JSON sintatticamente validi.
- [x] Tutti i dieci input incorporati rispettano `thesis-input.schema.json`.
- [x] Tutti i dieci output incorporati rispettano `thesis-output.schema.json`.
- [x] Ogni `evidenceFields` dei dieci casi contiene soltanto campi ammessi, presenti e non nulli nell'input.
- [x] Gli esempi coprono i dieci scenari dichiarati senza identificatori, simboli o `scenarioType` duplicati.
- [x] La matrice complessiva esercita tutte le cinque classificazioni ammesse.
- [x] I dieci esempi non contengono ordini o raccomandazioni buy/sell/hold, dati esterni o valori inventati.
- [x] `humanReviewRequired` è `true` in tutti i casi dubbi, rischiosi, contraddittori, obsoleti o insufficienti.
- [x] TRAIN-01 non richiede modello, librerie AI, Docker di training o GPU.

## Dataset Test Matrix

| Scenario | Expected result |
|---|---|
| `example-001.json` | Valido; `POTENTIALLY_UNDERVALUED`; evidenze positive; revisione umana non richiesta |
| `example-002.json` | Valido; `UNDER_REVIEW`; value-trap e warning deterministici; revisione umana richiesta |
| `example-003.json` | Valido; `INSUFFICIENT_DATA`; indicatori null/indisponibili; revisione umana richiesta |
| `example-004.json` | Impresa solida ma sopravvalutata; prezzo e qualità distinti; nessuna istruzione operativa |
| `example-005.json` | Fair value; classificazione `FAIRLY_VALUED`; tesi bilanciata |
| `example-006.json` | Dividendo elevato ma insostenibile; payout/warning evidenziati; revisione umana richiesta |
| `example-007.json` | Leva finanziaria elevata; rischio collegato ai campi disponibili; revisione umana richiesta |
| `example-008.json` | FCF negativo o in deterioramento; bear case presente e nessun dato inventato |
| `example-009.json` | Indicatori contraddittori; `UNDER_REVIEW` e revisione umana richiesta |
| `example-010.json` | Dati obsoleti; warning esplicito e revisione umana richiesta |
| Campo input aggiuntivo | Rifiutato da `additionalProperties: false` |
| Classificazione non ammessa | Rifiutata dallo schema output |
| Confidence fuori da 0–1 | Rifiutata dallo schema output |
| Evidence field non ammesso o assente | Rifiutato dal controllo del contratto/evidenze |
| JSON incorporato malformato | Parsing fallito e record non accettato |
| JSONL racchiuso in array | Non accettato come seed dataset TRAIN-01 |
| Dataset con numero righe diverso da dieci | Acceptance check fallito |
| ID, simbolo o scenario duplicato | Dataset rifiutato |
| Numero nell'output non riconducibile all'input | Dataset rifiutato, esclusa `confidence` |
| BUY/SELL/HOLD, Markdown o testo fuori JSON | Dataset rifiutato |

## Regression Checks

- [x] Nessun file in `backend/`, `frontend/` o nelle configurazioni Compose VIS è modificato.
- [x] Nessuna dipendenza AI è aggiunta al progetto.
- [x] Nessun token, segreto, peso modello, cache, checkpoint o adapter è tracciato.
- [x] Le modifiche di implementazione restano sotto `vis-model-training/`; le sole altre modifiche sono i documenti della spec TRAIN-01.
- [x] TRAIN-02 resta incompleta: nessuna CLI Python, report CI o suite di almeno 15 test viene anticipata.
- [x] `git diff --check` passa.

## Verification Commands

```bash
# JSON syntax for schemas and examples
jq empty vis-model-training/schemas/*.json
jq empty vis-model-training/examples/*.json

# Contract, evidence, scenarios and examples/JSONL identity
node vis-model-training/scripts/validate-dataset.mjs

# Exactly ten valid, non-empty JSONL records
test "$(grep -cve '^[[:space:]]*$' vis-model-training/datasets/seed-dataset-v1.jsonl)" -eq 10
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
6. Confermare che i dieci scenari siano distinti e che coprano tutte le classificazioni ammesse.
7. Cercare indicazioni buy/sell/hold, Markdown, informazioni esterne e numeri non supportati; il risultato deve essere vuoto.
8. Confermare che non siano stati eseguiti download, setup AI o accessi GPU.

## Merge Gate

TRAIN-01 è merge-ready quando esistono dieci esempi sintetici distinti e il JSONL contiene esattamente gli stessi dieci record; sintassi, schemi, evidenze, numeri, classificazioni, warning e revisione umana superano i controlli automatici e manuali; tutte le cinque classificazioni e i dieci scenari sono coperti; nessun software di training, deliverable TRAIN-02 o cambiamento runtime VIS è stato introdotto. Solo a quel punto README e roadmap TRAIN possono marcare TRAIN-01 completa.
