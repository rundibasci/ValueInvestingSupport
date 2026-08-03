# TRAIN-02 — Dataset Validator

## Context

TRAIN-01 ha consegnato due JSON Schema, un prompt versionato, dieci esempi sintetici e un seed dataset JSONL di dieci record che copre tutte le cinque classificazioni. `vis-model-training/scripts/validate-dataset.mjs` verifica oggi questi artefatti, ma resta un controllo prototipale e specifico della fase: non offre una CLI parametrica, errori stabili, report machine-readable o una suite di regressione sufficiente per proteggere le fasi di espansione del dataset.

TRAIN-02 trasforma quelle regole in un package Python riproducibile e riutilizzabile. Il validator impedisce che record formalmente o semanticamente errati entrino nei dataset TRAIN, senza caricare modelli, usare GPU o modificare il runtime VIS. Il validatore Node resta temporaneamente disponibile come oracle di parità sul seed dataset e sulle regole condivise.

## Scope

### Package e dipendenze

- Creare `vis-model-training/pyproject.toml` con Python `>=3.9`, package sotto `src/vis_training/` e dipendenze separate runtime/dev.
- Usare `jsonschema` per JSON Schema Draft 2020-12 e `pytest` per i test.
- Commettere un requirements lockato con versioni esatte compatibili con l'ambiente locale; l'ambiente virtuale resta `vis-model-training/.venv/` e non viene tracciato.
- Non aggiungere dipendenze Python, plugin o configurazioni al backend/frontend VIS.

### Componenti di validazione

- `schema_validator.py`: carica e verifica gli schemi, abilita il controllo `format: date` e valida input/output incorporati.
- `semantic_validator.py`: applica le regole di grounding, revisione umana, contenuto e coerenza definite dalla roadmap TRAIN.
- `dataset_validator.py`: legge JSONL in streaming, coordina i controlli per record, rileva `exampleId` duplicati e produce un risultato aggregato deterministico.
- Gli errori di un record non interrompono l'analisi degli altri record quando il file resta leggibile; il report deve mostrare tutte le violazioni rilevabili in sicurezza.

### Regole strutturali

- File JSONL leggibile, una struttura JSON per ogni riga non vuota e nessun array esterno.
- Documento conversazionale con esattamente tre messaggi nei ruoli `system`, `user`, `assistant` e metadata obbligatori.
- `user.content` e `assistant.content` contengono un solo oggetto JSON valido.
- Input e output conformi agli schemi forniti da CLI, inclusi proprietà aggiuntive, enum, range e date reali.
- `metadata.exampleId` univoco nel dataset; simbolo e scenario possono ripetersi nei dataset futuri e non costituiscono una chiave globale.

### Regole semantiche

- Ogni `evidenceFields` cita un campo ammesso, presente e non nullo nell'input.
- `dataQuality=INSUFFICIENT` richiede `classification=INSUFFICIENT_DATA` e revisione umana.
- Dati `INSUFFICIENT`, `INCONSISTENT`, `STALE` o materialmente contraddittori richiedono `humanReviewRequired=true`.
- Trend fortemente negativi richiedono almeno un elemento in `bearCase`.
- I testi di output non contengono istruzioni `BUY`, `SELL`, `HOLD`, Markdown o testo esterno all'oggetto JSON.
- Ogni numero menzionato nei testi dell'output deve essere presente nell'input, con la sola eccezione del campo strutturale `confidence`.
- Le regole non ricalcolano DCF, valore intrinseco, margin of safety, Value Score o altre metriche VIS.

### CLI

- Esporre `python -m scripts.validate_dataset` con parametri obbligatori `--dataset`, `--input-schema` e `--output-schema`.
- Supportare `--format text|json` con default `text` e `--output <path>` opzionale; senza `--output`, il report va su stdout.
- Risolvere i percorsi rispetto alla working directory, senza assumere il nome o la posizione del seed dataset.
- Non stampare record completi, prompt, payload grezzi o valori potenzialmente sensibili nei diagnostici.

### Report

- Il report JSON usa un contratto versionato con almeno: `formatVersion`, `dataset`, `records`, `valid`, `invalid`, `warnings`, `errors` e `diagnostics`.
- Ogni diagnostico include `line`, `exampleId` quando disponibile, `code`, `path`, `severity` e un messaggio sanitizzato.
- Il report testuale presenta lo stesso riepilogo e diagnostici nello stesso ordine deterministico.
- Un dataset valido di dieci record produce `records: 10`, `valid: 10`, `invalid: 0`, `warnings: 0`.

### Codici di uscita ed errore

- Exit `0`: validazione completata senza errori.
- Exit `1`: dataset analizzato ma con una o più violazioni di validazione.
- Exit `2`: errore di invocazione, configurazione, schema o I/O che impedisce la validazione.
- Exit `3`: errore interno inatteso, senza stack trace nel report normale.
- Definire codici diagnostici stabili almeno per: parsing JSONL, struttura conversazione, schema input/output, metadata, ID duplicato, evidence assente/nulla, classificazione insufficiente, revisione umana, bear case, raccomandazione proibita, Markdown, numero non supportato e testo fuori JSON.

### Test e CI readiness

- Aggiungere almeno 15 test automatici tra unitari e integrazione CLI.
- Usare fixture sintetiche positive e negative separate dal seed dataset ufficiale.
- Verificare parità fra Python e Node sull'esito del seed dataset e su un sottoinsieme rappresentativo di violazioni condivise.
- Fornire un comando deterministico che installa dipendenze bloccate, esegue i test e valida il seed dataset senza rete, modello o GPU dopo il setup iniziale.

## Decisions

1. **Python è l'implementazione finale di TRAIN-02.** Il validatore Node resta un oracle temporaneo e non viene esteso con nuove responsabilità della fase.
2. **Validator generico, gate seed specifico.** La CLI non codifica nomi file, numero record, simboli o scenari; la validation della fase verifica separatamente che il seed ufficiale contenga i dieci casi TRAIN-01.
3. **Errori collezionati e deterministici.** Il validator continua sui record successivi quando possibile e ordina i diagnostici per riga, percorso e codice.
4. **Contratti pubblici versionati.** Codici errore, exit code e report JSON sono compatibilità esterna per CI e fasi TRAIN successive.
5. **Nessun contenuto grezzo nei report.** I diagnostici identificano posizione e regola violata senza duplicare dati o prompt.
6. **Dipendenze isolate.** Python e le librerie TRAIN restano sotto `vis-model-training/` e non entrano nel runtime applicativo VIS.

## Out of Scope

- Modifica degli schemi, del prompt o dei dieci esempi TRAIN-01 salvo correzione indispensabile e documentata.
- Generazione o ampliamento del dataset oltre alle fixture di test sintetiche.
- Benchmark o download di Gemma, teacher inference, QLoRA, GPU/CUDA o container di training.
- Integrazione runtime con backend, frontend, API o database VIS.
- GitHub Actions o altri provider CI specifici: TRAIN-02 rende il comando CI-ready, mentre l'integrazione in una pipeline remota richiede autorizzazione separata.

## Compatibility and Risks

- Python 3.9 è il minimo imposto dall'ambiente locale; dipendenze e sintassi devono essere testate su questa versione.
- Una divergenza silenziosa fra Node e Python può cambiare l'accettazione dei dati. La parità deve essere misurata prima di deprecare il prototipo.
- Regole troppo legate ai dieci casi impedirebbero l'espansione successiva. Solo `exampleId` è globalmente univoco; simboli e categorie non devono essere hard-coded.
- Messaggi diagnostici che includono payload grezzi possono propagare dati non autorizzati nei log CI; tutti i messaggi devono essere sanitizzati.
- Il lock deve essere rigenerabile e non deve includere percorsi locali, token o indici privati con credenziali.
