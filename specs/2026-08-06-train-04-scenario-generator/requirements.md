# TRAIN-04 — Generatore di scenari finanziari

## Context

TRAIN-03 ha prodotto una baseline riproducibile di `google/gemma-3-4b-it` e ha evidenziato errori sistematici: predominio del margin of safety sui segnali di rischio, inversione della sopravvalutazione, mancata escalation dei value trap, payout ratio fuori scala ignorato, soglie qualitative inventate e gestione debole di dati stale, incoerenti o avversariali.

TRAIN-04 crea un catalogo di input finanziari sintetici controllati con cui TRAIN-05 potrà chiedere a un teacher di produrre risposte candidate. Il generatore produce soltanto scenari e input: non genera tesi, non usa un LLM e non dipende da aziende, quotazioni o provider reali. TRAIN-01 rimane il contratto del task; TRAIN-02 rimane il validatore; TRAIN-03 rimane un benchmark congelato e separato dal training.

## Scope

### Catalogo e contratti

- Definire un catalogo versionato con almeno le 14 categorie:
  - `UNDERVALUED_STRONG`
  - `UNDERVALUED_WEAK`
  - `VALUE_TRAP`
  - `OVERVALUED_STRONG`
  - `FAIR_VALUE`
  - `DIVIDEND_SAFE`
  - `DIVIDEND_RISK`
  - `HIGH_LEVERAGE`
  - `FCF_DETERIORATION`
  - `CONTRADICTORY_SIGNALS`
  - `STALE_DATA`
  - `INSUFFICIENT_DATA`
  - `INCONSISTENT_DATA`
  - `ADVERSARIAL_INPUT`
- Assegnare a ogni scenario un identificatore univoco `SCN-XXXXXX`, tipo, difficoltà, versione del generatore, seed derivato, regola/variante e input conforme a `schemas/thesis-input.schema.json`.
- Versionare separatamente catalogo, configurazione di distribuzione e formato record, così una modifica futura non altera silenziosamente `scenarios-v1.jsonl`.

### Generazione deterministica

- Implementare in Python un generatore rule-based senza rete, provider esterni o GPU.
- Accettare seed globale, quantità, configurazione e percorso output tramite CLI.
- Derivare un seed stabile per ogni record in modo che ordine, contenuto e report siano riproducibili.
- Usare quote, valori intrinseci, score, payout, leva e trend sintetici entro domini dichiarati.
- Calcolare o scegliere valori coerenti per scenario; le incoerenze sono ammesse soltanto nelle categorie che le richiedono e devono essere dichiarate nella variante.
- Usare simboli e nomi inequivocabilmente sintetici, senza imitare ticker o società reali.

### Regole finanziarie

- `marketPrice` deve essere maggiore di zero.
- `intrinsicValue` deve essere nullo oppure non negativo.
- `marginOfSafetyPercent` deve essere coerente con prezzo e valore intrinseco, eccetto una variante esplicitamente incoerente.
- `valueScore` deve essere nullo oppure compreso tra 0 e 100.
- Payout negativo è ammesso solo in varianti con earnings negativi o semantica esplicita; payout superiore al 100% deve essere trattato come segnale forte nei casi `DIVIDEND_RISK`.
- Leva, yield e value score non ricevono etichette universali come “alto”, “attraente” o “buono” senza una regola esplicita nel contesto.
- `STRONGLY_DECLINING` per utili o free cash flow deve comparire in varianti che rendano visibile il rischio operativo.
- Warning, qualità dati e valori nulli devono essere coerenti con scenario e variante.
- Test avversariali possono contenere testo non attendibile soltanto dentro campi dati ammessi; non possono modificare il contratto o introdurre segreti.

### Distribuzione iniziale

- Generare esattamente 500 scenari candidati:
  - 300 ordinari (60%);
  - 125 difficili (25%);
  - 75 avversariali o incompleti (15%).
- Coprire tutte le 14 categorie e tutte le varianti obbligatorie del catalogo.
- Evitare che la distribuzione casuale produca categorie vuote o quote instabili: i conteggi derivano da un piano deterministico, poi le variazioni numeriche usano il seed.
- Includere varianti mirate alle debolezze TRAIN-03: overvaluation direzionale, value trap con MoS positivo, payout ratio oltre 100%, dati stale/inconsistenti, evidenze contraddittorie e input avversariali.

### Validazione e report

- Validare ogni `input` con lo schema TRAIN-01 e con invarianti semantiche specifiche dello scenario.
- Fallire l’intera generazione senza promuovere un dataset parziale se un record è invalido, duplicato o incoerente in modo non intenzionale.
- Produrre `datasets/candidates/scenarios-v1.jsonl` in ordine deterministico.
- Produrre `reports/scenarios/distribution-v1.json` con conteggi per categoria, difficoltà, variante, warning e presenza/nullabilità dei campi, oltre a seed, versione e hash del dataset.
- Verificare contaminazione e identità rispetto a seed TRAIN-01 e benchmark TRAIN-03; nessun `exampleId`, `scenarioId` o record serializzato può essere riutilizzato.

## Decisions

1. **Il generatore è rule-based e locale.** Python è già il linguaggio del tooling TRAIN e consente test veloci, deterministici e senza costi cloud.
2. **Il teacher è escluso.** TRAIN-04 produce soltanto input; output attesi e candidati appartengono a TRAIN-05 e TRAIN-06.
3. **Le quote di difficoltà sono esatte.** Per 500 record vengono generati 300/125/75 casi, evitando oscillazioni statistiche che renderebbero meno confrontabili le versioni.
4. **Il seed controlla anche la serializzazione.** A parità di codice, configurazione e seed, dataset e report devono essere byte-identici.
5. **Le incoerenze sono dati etichettati, non bug tollerati.** Ogni eccezione alle invarianti generali deve appartenere a una categoria/variante che la dichiara.
6. **Nessuna soglia finanziaria universale implicita.** I numeri restano evidenze; eventuali soglie usate per costruire una variante sono dichiarate nel catalogo e non presentate come regole generali di investimento.
7. **Il dataset candidato non modifica benchmark o seed.** TRAIN-01 e TRAIN-03 sono input read-only del gate di contaminazione.
8. **Nessuna dipendenza applicativa VIS.** Backend, frontend, database e runtime non vengono modificati.

## Out of Scope

- Invocazione di modelli teacher o critic.
- Generazione di risposte assistant, ground truth o preferenze.
- Curazione/accettazione del dataset di training.
- Fine-tuning, QLoRA, GPU o configurazione RunPod.
- Dati, nomi, ticker, filing o quotazioni di aziende reali.
- Modifiche a schemi e prompt TRAIN-01, benchmark TRAIN-03 o runtime VIS.
- Nuove API, migrazioni database o interfacce frontend.

## Compatibility and Risks

- Il record wrapper degli scenari è distinto dall’input tesi, ma `input` deve restare compatibile con lo schema TRAIN-01 e con la futura teacher pipeline.
- Valori casuali in virgola mobile possono compromettere la riproducibilità: normalizzazione, arrotondamento e serializzazione JSON devono essere espliciti.
- Un catalogo sbilanciato può insegnare scorciatoie al modello: il report espone distribuzioni e combinazioni, non soltanto il totale.
- Varianti troppo simili possono produrre duplicati semantici: il generatore deve controllare identità esatta e segnalare collisioni.
- Casi avversariali sono dati non attendibili: devono rimanere sintetici, sanitizzati e privi di token o istruzioni operative reali.
- Nessun output costituisce consiglio finanziario; la fase prepara dati per un sistema di supporto decisionale conforme al confine MiFID II definito dalla missione.
