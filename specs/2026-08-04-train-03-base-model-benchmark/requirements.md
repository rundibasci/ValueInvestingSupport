# TRAIN-03 — Benchmark del modello base

## Context

TRAIN-01 ha definito il contratto dell'Investment Thesis Agent e TRAIN-02 ha consegnato il validator Python generico che protegge struttura, schema e regole semantiche dei dataset TRAIN. Prima di generare esempi su larga scala o avviare QLoRA serve una baseline misurata di `google/gemma-3-4b-it`: senza questa evidenza non è possibile attribuire un eventuale miglioramento all'adapter.

TRAIN-03 costruisce un benchmark congelato e separato dal training, esegue il modello base in modo deterministico su RunPod Secure Cloud e produce risultati riproducibili, metriche automatiche, revisione umana e una tassonomia degli errori. VIS resta il motore deterministico autorevole; il modello interpreta soltanto il contesto fornito.

## Scope

### Contratti e ambiente riproducibile

- Usare `google/gemma-3-4b-it` in BF16, fissando prima dell'esecuzione revision immutabili di modello e tokenizer.
- Usare RunPod Secure Cloud con una singola NVIDIA L4 da 24 GB, Linux, almeno 48 GB RAM e almeno 100 GB fra volume persistente e spazio temporaneo disponibile.
- Versionare l'immagine Docker tramite digest e bloccare Python, PyTorch, Transformers e ogni dipendenza diretta in un lock riproducibile.
- Registrare immagine, dipendenze, modello, tokenizer, prompt, dataset, commit Git, hardware, driver NVIDIA, CUDA visibile da PyTorch e configurazione di inferenza in un manifest per ogni run.
- Mantenere il Mac M4 locale per authoring, validazione, metriche e test; i risultati canonici derivano esclusivamente dalla run RunPod approvata.

### Dataset benchmark

- Creare `vis-model-training/datasets/benchmark/base-benchmark-v1.jsonl` con almeno 50 casi sintetici, distinti dai dati di training futuri.
- Coprire almeno: 7 sottovalutazioni robuste, 7 value trap, 6 sopravvalutazioni, 5 fair value, 5 dividendi a rischio, 5 dati insufficienti, 5 dati obsoleti, 5 contraddizioni e 5 casi avversariali.
- Ogni caso contiene classificazione attesa, requisiti di revisione umana e metadati di categoria sufficienti al calcolo delle metriche, senza includere output del modello base come ground truth.
- Tutti i record devono superare la CLI TRAIN-02 prima del congelamento.
- Calcolare e registrare hash SHA-256 del benchmark, degli schemi e del prompt; dopo la prima inferenza canonica il benchmark è immutabile e ogni cambiamento richiede una nuova versione.
- Escludere società reali, dati personali, dati proprietari, testi protetti non autorizzati, credenziali e contenuti teacher.

### Inferenza del modello base

- Eseguire un runner batch text-only con `temperature=0.0`, `do_sample=false`, `max_new_tokens=1024` e batch size iniziale `1`.
- Fissare seed applicativi e framework, modalità evaluation e ogni opzione di attenzione; registrare eventuali limiti di determinismo residui del backend CUDA.
- Disabilitare TorchDynamo/Inductor per la baseline L4 dopo l'`InductorError` riprodotto nello smoke test e fissare esplicitamente il processor lento con `use_fast=false`.
- Applicare esattamente il prompt e il template chat versionati, senza retry che nascondano il primo output.
- Usare `system-prompt-v2.txt`, che include il contratto output completo; il prompt v1 e i relativi smoke output sono esclusi dalla baseline perché lo schema citato non era realmente fornito al modello.
- Conservare separatamente primo output grezzo, esito del parsing, output parsato quando valido, latenza, conteggio token ed errore sanitizzato per ogni caso.
- Consentire una run pilota non canonica su un sottoinsieme prima della run completa; i relativi output non entrano nel report finale.
- La run canonica deve poter ripartire senza sovrascrivere risultati esistenti e deve identificare record mancanti o duplicati.

### Metriche automatiche

- Calcolare JSON validity rate, schema compliance rate, classification accuracy, evidence-field precision, unsupported numeric claim rate, prohibited recommendation rate, human-review accuracy, exact field coverage, lunghezza media output e latenza.
- Definire formalmente denominatori, handling degli output non parsabili e arrotondamenti prima di leggere i risultati finali.
- Produrre metriche globali e per categoria, conteggi assoluti e riferimenti agli ID dei casi falliti.
- Riutilizzare gli schemi e le regole TRAIN-02 senza modificare i risultati grezzi.

### Revisione umana ed error analysis

- Selezionare deterministicamente almeno 20 casi, includendo tutte le categorie e tutti i principali tipi di errore osservati.
- Valutare correttezza della sintesi, equilibrio bull/bear, qualità dei rischi, aderenza all'input e utilità per un revisore umano con una rubrica versionata.
- Separare valutazione della qualità narrativa dai controlli automatici di schema e grounding.
- Classificare gli errori principali e descrivere almeno tre comportamenti misurabili da migliorare nelle fasi successive.
- Conservare identità del revisore solo se necessaria e in forma non sensibile; nessuna review è sostituita da un secondo modello in TRAIN-03.

### Report e gestione RunPod

- Produrre gli artefatti sotto `vis-model-training/reports/baseline/gemma-3-4b-it-v1/`, separando manifest, metriche, risultati per caso, review e sintesi.
- Commettere soltanto report testuali/JSON piccoli, sanitizzati e utili alla riproducibilità; pesi, cache, checkpoint e output temporanei restano esclusi.
- Scaricare e verificare gli artefatti prima di terminare il Pod.
- Documentare costo stimato e consuntivo, durata e identificatore non segreto della run.
- Arrestare e rimuovere compute, volume e IP fatturabili dopo il recupero verificato degli artefatti, salvo decisione esplicita di conservarli.

## Decisions

1. **RunPod Secure Cloud è l'ambiente canonico.** Community Cloud e il Mac locale possono supportare prove non canoniche, ma non producono la baseline ufficiale.
2. **L4 24 GB e BF16 sono il profilo iniziale.** TRAIN-03 misura il checkpoint base senza quantizzazione; A40/L40S sono fallback compatibili se L4 non è disponibile, ma il cambio deve essere registrato e approvato prima della run canonica.
3. **Benchmark congelato prima dei risultati.** Casi, label, rubriche e formule metriche vengono versionati e hashati prima dell'inferenza completa.
4. **Primo output preservato.** Retry e correzioni possono essere analizzati separatamente, ma non sostituiscono il primo tentativo nelle metriche canoniche.
5. **Separazione rigorosa dal training.** I casi benchmark non entrano in TRAIN-04/05/06 né in dataset usati per fine-tuning.
6. **Nessun segreto nel repository.** Token Hugging Face e credenziali RunPod sono forniti tramite RunPod Secrets o variabili effimere e non appaiono in file, log, history, manifest o report.
7. **Nessuna spesa automatica.** Creazione e avvio del Pod richiedono un'azione esplicita dell'utente dopo approvazione della spec e dei costi.

## Out of Scope

- QLoRA, PEFT, training, adapter, optimizer o modifica dei pesi del modello.
- Generazione teacher e uso di `google/gemma-3-27b-it`.
- Generatore di scenari TRAIN-04 e ampliamento del dataset di training.
- Quantizzazione come sostituto della baseline BF16.
- Integrazione runtime con backend, frontend, API o database VIS.
- Valutazione comparativa base-versus-adapter, appartenente a TRAIN-09.
- Distribuzione commerciale o servizio customer-facing.
- Automazione che crea, ricarica o mantiene risorse RunPod senza conferma dell'utente.

## Compatibility and Risks

- La L4 24 GB è il target approvato ma BF16, lunghezza input e implementazione del modello devono essere verificati con uno smoke test; un OOM richiede tuning non semantico documentato o approvazione del fallback GPU.
- Kernel CUDA e librerie possono introdurre non-determinismo residuo anche con decoding greedy. La riproducibilità è definita da ambiente, manifest e tolleranze esplicite, non dall'assunzione di output byte-identici fra hardware diversi.
- Un benchmark scritto dopo aver osservato gli output sovrastima la qualità. Dataset, label, rubriche e formule devono essere congelati prima della run canonica.
- Output grezzi possono contenere testo inatteso o proibito. Devono essere trattati come dati non fidati, mai eseguiti e sanitizzati prima di log o report condivisi.
- Cache modello e report possono consumare storage fatturabile anche a Pod fermo. Il runbook deve includere esportazione, checksum e rimozione finale delle risorse.
- L'accesso al checkpoint Gemma può richiedere accettazione dei termini e token Hugging Face autorizzato; la verifica avviene senza esporre il token.
- La revisione legale esterna registrata in TRAIN-00 resta obbligatoria prima di distribuzione commerciale o uso customer-facing.
