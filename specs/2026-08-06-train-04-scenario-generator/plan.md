# TRAIN-04 — Implementation Plan

## 1. Contratti, catalogo e configurazione

1. Definire formato versione 1.0 del record scenario, inclusi `scenarioId`, `scenarioType`, `difficulty`, `generatorVersion`, `seed`, `variantId` e `input`.
2. Creare il catalogo versionato delle 14 categorie con varianti, invarianti, eccezioni intenzionali e difficoltà ammesse.
3. Creare una configurazione di distribuzione che assegni esattamente 300 casi ordinari, 125 difficili e 75 avversariali/incompleti.
4. Dichiarare seed canonico, versione del generatore, regole di ordinamento, arrotondamento e serializzazione.
5. Mappare esplicitamente le failure class TRAIN-03 alle varianti obbligatorie TRAIN-04.

## 2. Motore di generazione deterministico

1. Implementare primitive pure per numeri sintetici, enum, nullabilità, warning e trend.
2. Implementare una factory per ciascuna categoria, mantenendo separate regole comuni ed eccezioni intenzionali.
3. Derivare seed per record e identificatori stabili senza dipendere dall’ordine di esecuzione del processo.
4. Normalizzare decimali e serializzazione JSON per garantire output byte-identico.
5. Generare simboli e nomi sintetici che non coincidano con aziende reali o record TRAIN-01/TRAIN-03.

## 3. Invarianti e contaminazione

1. Riutilizzare la validazione dello schema TRAIN-01 per ogni `input` generato.
2. Implementare invarianti generali per prezzo, valore intrinseco, MoS, score, payout, trend, warning e qualità dati.
3. Implementare invarianti specifiche per categoria e variante, compresa la whitelist delle incoerenze intenzionali.
4. Rifiutare ID duplicati, record esatti duplicati e collisioni con seed TRAIN-01 o benchmark TRAIN-03.
5. Assicurare che nessun artifact parziale venga promosso quando la generazione o la validazione fallisce.

## 4. CLI e scrittura atomica

1. Aggiungere comandi CLI per generare, validare e verificare la riproducibilità degli scenari.
2. Supportare `--seed`, `--count`, `--catalog`, `--config`, `--output` e `--report` con default documentati.
3. Scrivere dataset e report tramite file temporanei e sostituzione atomica solo dopo il superamento di tutti i gate.
4. Restituire exit code distinti per configurazione invalida, scenario invalido, contaminazione e failure interna.
5. Produrre errori sanitizzati con scenario, categoria, variante e regola violata.

## 5. Dataset candidato e report di distribuzione

1. Generare i 500 record canonici in `datasets/candidates/scenarios-v1.jsonl`.
2. Produrre `reports/scenarios/distribution-v1.json` con conteggi e percentuali per categoria, difficoltà, variante, warning e disponibilità dei campi.
3. Registrare seed, versione, hash catalogo/configurazione e SHA-256 del dataset.
4. Verificare manualmente campioni rappresentativi delle 14 categorie e tutte le failure class derivate da TRAIN-03.
5. Documentare limiti noti e handoff previsto verso TRAIN-05.

## 6. Test e merge readiness

1. Aggiungere unit test per ogni factory, invariante, eccezione intenzionale e boundary numerico.
2. Aggiungere test property-based o parametrizzati per riproducibilità, range e combinazioni di seed.
3. Aggiungere test CLI per successo, argomenti invalidi, collisioni, output atomico e codici di uscita.
4. Rigenerare due volte gli artifact canonici e confrontarli byte per byte.
5. Rieseguire l’intera suite TRAIN-01–TRAIN-03, i validatori dataset, il freeze TRAIN-03 e il controllo segreti.
6. Aggiornare `vis-model-training/README.md` e marcare TRAIN-04 completa soltanto dopo il superamento del merge gate.
