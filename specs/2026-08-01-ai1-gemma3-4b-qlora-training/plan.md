# TRAIN-01 — Implementation Plan

## 1. Struttura del percorso parallelo

1. Creare `vis-model-training/` separata dal backend Java.
2. Aggiungere un README che descriva scopo, confine VIS/modello, struttura, versionamento e validazione manuale.
3. Creare le directory `schemas/`, `prompts/`, `examples/` e `datasets/`.

## 2. Contratti JSON Schema

1. Creare `schemas/thesis-input.schema.json` copiando fedelmente il contratto Draft 2020-12 fornito in TRAIN-01.
2. Creare `schemas/thesis-output.schema.json` copiando fedelmente classificazioni, campi obbligatori e definizione delle evidenze forniti in TRAIN-01.
3. Verificare `additionalProperties: false`, tipi nullable, range numerici, enumerazioni e riferimenti `$ref`.
4. Verificare che le sole classificazioni ammesse non siano raccomandazioni di investimento.

## 3. System prompt versionato

1. Creare `prompts/system-prompt-v1.txt` con il testo e le dodici regole definite dal piano.
2. Verificare che proibisca dati inventati, ricalcoli, istruzioni buy/sell/hold e testo fuori dal JSON.
3. Verificare che imponga evidenze, separazione valutazione/qualità e revisione umana per dati problematici.

## 4. Esempi manuali sintetici

1. Creare `example-001.json` per `UNDERVALUED_STRONG_BUSINESS` usando `VIS1`.
2. Creare `example-002.json` per `VALUE_TRAP` usando `VIS2`.
3. Creare `example-003.json` per `INSUFFICIENT_DATA` usando `VIS3`.
4. Conservare esattamente il formato conversazionale e i metadati forniti.
5. Controllare manualmente che nessun claim introduca informazioni esterne o numeri non presenti nell'input.

## 5. Seed dataset JSONL

1. Creare `datasets/seed-dataset-v1.jsonl` con i tre documenti, uno per riga.
2. Non racchiudere le righe in un array JSON.
3. Verificare che le stringhe JSON nei contenuti `user` e `assistant` siano serializzate correttamente.
4. Verificare che il file contenga esattamente tre righe non vuote e corrisponda agli esempi sorgente.

## 6. Validazione e readiness

1. Analizzare sintatticamente schemi, esempi, JSON incorporato e righe JSONL usando strumenti già disponibili, senza introdurre dipendenze AI.
2. Validare input e output incorporati contro i rispettivi schemi con un validatore JSON Schema Draft 2020-12 disponibile localmente; in assenza, documentare chiaramente il controllo manuale anziché installare librerie AI.
3. Verificare i riferimenti `evidenceFields`, la coerenza delle classificazioni e `humanReviewRequired` nei casi dubbi.
4. Verificare assenza di buy/sell/hold, dati aziendali reali e contenuti esterni.
5. Eseguire `git diff --check` e confermare che nessuna superficie runtime VIS sia cambiata.
