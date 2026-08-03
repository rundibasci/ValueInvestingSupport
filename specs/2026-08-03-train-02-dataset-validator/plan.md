# TRAIN-02 — Implementation Plan

## 1. Packaging e contratti pubblici

1. Creare `vis-model-training/pyproject.toml` per Python 3.9+, layout `src/` e dipendenze `jsonschema`/`pytest` separate per runtime e sviluppo.
2. Generare e commettere un requirements lockato con versioni esatte, privo di riferimenti locali o credenziali.
3. Definire modelli/record Python per risultato aggregato e diagnostico, inclusi formato report, severity, codici stabili ed exit code.
4. Creare i package `src/vis_training/validation/`, `tests/validation/` e il package CLI `scripts/` con `__init__.py` e `validate_dataset.py`.

## 2. Validazione strutturale

1. Implementare caricamento sicuro degli schemi e selezione esplicita del validator Draft 2020-12.
2. Abilitare e testare il format checker delle date, inclusi giorni e mesi impossibili.
3. Implementare lettura JSONL per riga, parsing del documento conversazionale, ordine dei ruoli e parsing dei JSON incorporati.
4. Validare input/output contro gli schemi e tradurre gli errori della libreria in diagnostici stabili, ordinati e sanitizzati.
5. Rilevare metadata mancanti e `exampleId` duplicati senza imporre unicità a simboli o scenario type.

## 3. Validazione semantica

1. Implementare controlli su evidence field assenti, non ammessi o nulli.
2. Implementare coerenza fra data quality, classificazione e `humanReviewRequired`.
3. Richiedere un bear case per trend fortemente negativi.
4. Rifiutare raccomandazioni operative, Markdown, testo fuori JSON e numeri testuali non supportati dall'input.
5. Mantenere le regole indipendenti dai valori e dai nomi dei dieci casi seed.

## 4. Orchestrazione, report e CLI

1. Aggregare risultati validi/invalidi continuando sui record successivi quando il file resta analizzabile.
2. Implementare serializer JSON versionato e renderer testuale equivalenti.
3. Implementare `--dataset`, `--input-schema`, `--output-schema`, `--format` e `--output` con messaggi d'uso chiari.
4. Applicare gli exit code `0`, `1`, `2`, `3` e impedire stack trace/payload grezzi nell'output normale.
5. Verificare comportamento deterministico di conteggi e ordinamento diagnostici.

## 5. Fixture e test automatici

1. Creare fixture sintetiche minime per record valido, JSONL malformato, schema invalido, struttura conversazione errata e ID duplicato.
2. Aggiungere fixture per evidence assente/nulla, classificazione insufficiente errata, revisione umana mancante, bear case mancante, raccomandazione, Markdown, numero inventato e testo fuori JSON.
3. Scrivere almeno 15 test unitari/integrativi, includendo report text/JSON, scrittura `--output` ed exit code.
4. Verificare che i test non modifichino il seed dataset e non contengano società reali, segreti o output di modelli.

## 6. Parità e merge readiness

1. Eseguire Node e Python sui dieci record TRAIN-01 e confrontare esito, conteggi e regole condivise.
2. Validare un insieme rappresentativo di fixture negative con entrambi gli strumenti, documentando eventuali differenze intenzionali.
3. Aggiornare `vis-model-training/README.md` con setup, comando CLI effettivo, report ed esito TRAIN-02.
4. Eseguire suite Python, validatore Node, validatore Python sul seed, controllo del lock e `git diff --check`.
5. Marcare TRAIN-02 completa soltanto quando tutti i criteri di accettazione e il merge gate sono soddisfatti.
