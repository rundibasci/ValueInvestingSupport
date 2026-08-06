# TRAIN-04 — Validation

## Acceptance Criteria

- [x] Il catalogo contiene almeno 14 categorie e tutte le varianti obbligatorie derivate dagli errori TRAIN-03.
- [x] Il dataset canonico contiene esattamente 500 record validi e 500 `scenarioId` univoci.
- [x] La distribuzione è esattamente 300 ordinari, 125 difficili e 75 avversariali o incompleti.
- [x] Ogni input è conforme a `schemas/thesis-input.schema.json`.
- [x] Ogni scenario rispetta invarianti generali e specifiche, salvo eccezioni intenzionali dichiarate.
- [x] Prezzo, valore intrinseco, MoS, score, payout, trend, qualità dati e warning sono semanticamente coerenti con categoria e variante.
- [x] A parità di seed, versione, catalogo e configurazione, dataset e report sono byte-identici.
- [x] Seed differenti producono variazioni numeriche mantenendo identici conteggi, categorie e vincoli.
- [x] Nessun record o identificatore collide con TRAIN-01 o TRAIN-03.
- [x] Il report documenta distribuzione, nullabilità, warning, varianti, seed, versioni e hash.
- [x] La CLI fallisce in modo atomico e con exit code documentati su configurazioni o scenari invalidi.
- [x] Nessun modello, rete, GPU, provider reale o segreto è necessario.
- [x] Backend, frontend, database e runtime VIS restano invariati.

## Scenario Test Matrix

| Scenario | Expected result |
|---|---|
| `UNDERVALUED_STRONG` | Prezzo sotto valore intrinseco, MoS positivo forte e trend non deteriorati |
| `UNDERVALUED_WEAK` | Sconto limitato o segnali operativi meno solidi senza trasformarsi in value trap |
| `VALUE_TRAP` | MoS positivo combinato con deterioramento operativo e/o leva materialmente più gravosa nella variante |
| `OVERVALUED_STRONG` | Prezzo sopra valore intrinseco e MoS negativo coerente |
| `FAIR_VALUE` | Prezzo e valore intrinseco allineati entro la tolleranza dichiarata |
| `DIVIDEND_SAFE` | Yield supportato da payout e cash flow coerenti, senza dedurre sicurezza dal solo yield |
| `DIVIDEND_RISK` | Payout oltre 100% e/o FCF deteriorato; il segnale d’allarme è esplicito nella variante |
| `HIGH_LEVERAGE` | Leva costruita secondo la regola della variante, senza dichiararla soglia universale |
| `FCF_DETERIORATION` | Trend FCF declining/strongly declining coerente con la difficoltà |
| `CONTRADICTORY_SIGNALS` | Evidenze positive e negative più warning esplicito |
| `STALE_DATA` | Qualità stale e warning coerente, con valori presenti ma da revisionare |
| `INSUFFICIENT_DATA` | Campi essenziali nulli/non disponibili e warning sulla storia mancante |
| `INCONSISTENT_DATA` | Incoerenza intenzionale identificata dalla variante e dalla qualità dati |
| `ADVERSARIAL_INPUT` | Testo non attendibile resta in un campo dati ammesso e non altera il record |

## Boundary and Failure Matrix

| Scenario | Expected result |
|---|---|
| `marketPrice = 0` o negativo | Record rifiutato |
| `intrinsicValue < 0` | Record rifiutato |
| `valueScore < 0` o `> 100` | Record rifiutato |
| MoS incoerente in categoria ordinaria | Record rifiutato |
| MoS incoerente in variante whitelisted | Record accettato e classificato come incoerenza intenzionale |
| Payout negativo senza semantica esplicita | Record rifiutato |
| Payout `> 100%` in dividend risk | Record accettato e contabilizzato nel report |
| Trend fuori enum | Schema validation fallisce |
| Warning incompatibile con scenario | Validazione semantica fallisce |
| ID o record duplicato | Generazione fallisce senza sostituire l’output esistente |
| Collisione con TRAIN-01/TRAIN-03 | Gate di contaminazione fallisce |
| Scrittura interrotta | Nessun dataset canonico parziale visibile |
| Stesso seed ripetuto | Dataset e report byte-identici |
| Seed diverso | Valori variati, quote e invarianti preservate |

## Regression Checks

- [x] Tutti i test TRAIN esistenti continuano a passare (57 test totali).
- [x] Il seed TRAIN-01 resta valido e immutato.
- [x] Il benchmark e il freeze TRAIN-03 restano validi e immutati.
- [x] Schemi e prompt TRAIN-01 non cambiano.
- [x] Gli artifact baseline TRAIN-03 e i relativi checksum restano validi.
- [x] Nessuna dipendenza TRAIN entra nei moduli backend/frontend VIS.
- [x] Nessun token, segreto, dato reale o payload provider compare negli scenari o nei report.
- [x] Cache, file temporanei e output non intenzionali restano non tracciati.
- [x] `git diff --check` passa e la working tree finale contiene soltanto artifact intenzionali.

## Verification Commands

I comandi seguenti corrispondono ai moduli implementati.

```bash
cd vis-model-training
.venv/bin/python -m pytest
.venv/bin/python -m scripts.validate_dataset \
  --dataset datasets/seed-dataset-v1.jsonl \
  --input-schema schemas/thesis-input.schema.json \
  --output-schema schemas/thesis-output.schema.json
.venv/bin/python -m scripts.validate_dataset \
  --dataset datasets/benchmark/base-benchmark-v1.jsonl \
  --input-schema schemas/thesis-input.schema.json \
  --output-schema schemas/thesis-output.schema.json
node scripts/validate-dataset.mjs

# TRAIN-04 tooling; exact module names are established during implementation
.venv/bin/python -m pytest tests/scenarios
.venv/bin/python -m vis_training.scenarios.cli generate \
  --seed 20260806 \
  --count 500 \
  --output datasets/candidates/scenarios-v1.jsonl \
  --report reports/scenarios/distribution-v1.json
.venv/bin/python -m vis_training.scenarios.cli validate \
  --dataset datasets/candidates/scenarios-v1.jsonl
.venv/bin/python -m vis_training.scenarios.cli verify-reproducibility \
  --seed 20260806 \
  --count 500
env PYTHONPATH=src .venv/bin/python -m vis_training.benchmark.cli verify-freeze \
  --root . \
  --manifest datasets/benchmark/base-benchmark-v1.freeze.json

cd ..
git diff --check
git status --short
```

## Manual Validation

1. Ispezionare almeno due record per ciascuna delle 14 categorie.
2. Verificare tutte le varianti derivate da TRAIN-03: payout oltre 100%, overvaluation, value trap, stale, contradictions e adversarial.
3. Controllare che simboli e company name siano chiaramente sintetici.
4. Confrontare prezzo, valore intrinseco e MoS su un campione di casi ordinari e incoerenti intenzionali.
5. Verificare che il report sommi a 500 e riporti esattamente le quote 300/125/75.
6. Rigenerare in una directory temporanea e confrontare SHA-256 e byte degli artifact.
7. Cercare token, dati aziendali reali, istruzioni operative e contenuti non sanitizzati.
8. Confermare che nessuna risorsa cloud o GPU sia stata creata.

## Merge Gate

TRAIN-04 è merge-ready soltanto quando catalogo, configurazione, generatore, validatori e CLI sono testati; i 500 scenari sono conformi allo schema, semanticamente coerenti e distribuiti 300/125/75; riproducibilità e contaminazione sono verificate; dataset e report sono versionati e sanitizzati; tutte le regressioni TRAIN passano; runtime VIS resta invariato. TRAIN-05 non può generare output teacher prima del superamento di questo gate.
