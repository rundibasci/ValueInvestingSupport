# TRAIN-02 — Validation

## Acceptance Criteria

- [x] Esistono `pyproject.toml`, requirements lockato e package Python importabile su Python 3.9+.
- [x] Le sole dipendenze dirette della fase sono dichiarate e isolate sotto `vis-model-training/`.
- [x] La CLI accetta dataset e schemi arbitrari tramite i percorsi richiesti.
- [x] Il seed TRAIN-01 produce 10 record validi, zero invalidi, zero warning e exit `0`.
- [x] Errori validabili su singoli record vengono raccolti senza interrompere i record successivi.
- [x] Errori bloccanti di invocazione/schema/I/O producono exit `2`; errori inattesi producono exit `3` senza stack trace normale.
- [x] Report JSON e testuale espongono gli stessi conteggi e diagnostici in ordine deterministico.
- [x] I diagnostici usano codici stabili e non includono payload o prompt grezzi.
- [x] Sono implementati 31 test automatici con fixture positive e negative sintetiche.
- [x] Python e Node concordano sul seed ufficiale e sulle regole condivise selezionate.
- [x] Il validator non codifica i nomi, i simboli, gli scenari o il conteggio del seed TRAIN-01.
- [x] Nessun modello, GPU, servizio esterno o runtime VIS è richiesto.

## Test Matrix

| Scenario | Expected result |
|---|---|
| Seed ufficiale TRAIN-01 | 10 validi, 0 invalidi, 0 warning, exit `0` |
| Dataset alternativo valido | Accettato senza dipendere da nomi o conteggio TRAIN-01 |
| File dataset assente | Diagnostico I/O sanitizzato, exit `2` |
| Schema assente o non valido | Diagnostico schema/configurazione, exit `2` |
| Riga JSONL malformata | Record invalido con linea e codice stabile, exit `1` |
| JSONL racchiuso in array | Struttura dataset rifiutata, exit `1` |
| Ruoli mancanti, extra o fuori ordine | Record invalido, exit `1` |
| JSON user/assistant malformato o con testo esterno | Record invalido, exit `1` |
| Input/output fuori schema | Percorso schema e codice stabile, exit `1` |
| Data impossibile | Errore `format: date`, exit `1` |
| Metadata o `exampleId` mancante | Record invalido, exit `1` |
| `exampleId` duplicato | Record interessato segnalato, exit `1` |
| Simbolo o scenario ripetuto con ID univoci | Non rifiutato per la sola ripetizione |
| Evidence field assente, non ammesso o nullo | Record invalido, exit `1` |
| `INSUFFICIENT` con classificazione/review errata | Record invalido, exit `1` |
| `STALE`/`INCONSISTENT` senza human review | Record invalido, exit `1` |
| Trend fortemente negativo senza bear case | Record invalido, exit `1` |
| BUY/SELL/HOLD nel testo | Record invalido, exit `1` |
| Markdown nel testo | Record invalido, exit `1` |
| Numero testuale non presente nell'input | Record invalido, exit `1` |
| Report `--format json` | JSON conforme al contratto e senza payload grezzi |
| Report `--output` | File scritto; stdout non duplica il report |
| Errore interno simulato | Exit `3`, messaggio sanitizzato, nessuno stack trace normale |

## Regression Checks

- [x] `node vis-model-training/scripts/validate-dataset.mjs` continua a passare sui dieci casi.
- [x] Schemi, prompt, esempi e seed dataset TRAIN-01 restano invariati salvo correzione documentata.
- [x] Backend, frontend, Compose e dipendenze runtime VIS non cambiano.
- [x] `.venv`, cache, report temporanei e fixture generate non sono tracciati.
- [x] Il lock non contiene token, credenziali, URL autenticati o percorsi locali.
- [x] `git diff --check` passa.

## Verification Commands

```bash
cd vis-model-training

# Reproducible environment
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.lock
.venv/bin/python -m pip install --no-deps --no-build-isolation .

# Tests and Python CLI
.venv/bin/python -m pytest
.venv/bin/python -m scripts.validate_dataset \
  --dataset datasets/seed-dataset-v1.jsonl \
  --input-schema schemas/thesis-input.schema.json \
  --output-schema schemas/thesis-output.schema.json
.venv/bin/python -m scripts.validate_dataset \
  --dataset datasets/seed-dataset-v1.jsonl \
  --input-schema schemas/thesis-input.schema.json \
  --output-schema schemas/thesis-output.schema.json \
  --format json

# TRAIN-01 parity baseline
node scripts/validate-dataset.mjs

cd ..
git diff --check
git status --short
```

## Manual Validation

1. Confrontare report text e JSON sul seed e su una fixture con più violazioni.
2. Verificare che i diagnostici identifichino linea, codice e percorso senza mostrare il record completo.
3. Eseguire la CLI da `vis-model-training/` e dalla root usando percorsi espliciti.
4. Confermare che un dataset alternativo valido non sia vincolato ai dieci nomi TRAIN-01.
5. Confrontare esito Node/Python sul seed e sulle fixture di parità selezionate.
6. Verificare che nessun download modello, accesso GPU o modifica runtime VIS sia avvenuto.

## Merge Gate

TRAIN-02 è merge-ready quando il package Python e la CLI sono riproducibili su Python 3.9+, il seed di dieci record passa con report text/JSON coerenti, le fixture negative producono codici/exit code stabili, almeno 15 test passano, la parità Node/Python è documentata, i report sono sanitizzati e CI-ready, le superfici TRAIN-01/VIS restano integre e `git diff --check` è pulito. Il validatore Node può essere deprecato solo in una fase successiva dopo l'adozione del validator Python da parte dei consumer.
