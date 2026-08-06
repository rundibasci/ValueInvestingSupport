# TRAIN-05 — Teacher Pipeline

## Context

TRAIN-04 ha prodotto 500 scenari sintetici input-only, deterministici e separati dal benchmark TRAIN-03. TRAIN-05 usa un modello teacher più capace per generare risposte candidate strutturate, validarle e sottoporle a un critic indipendente. I candidati restano materiale grezzo: soltanto TRAIN-06 potrà curarli e promuoverli in un dataset di training.

TRAIN-00 ha selezionato `google/gemma-3-27b-it` come teacher iniziale e `google/gemma-3-12b-it` come fallback soggetto ad aggiornamento ADR. La selezione autorizza la valutazione, non l’accettazione automatica degli output. Il teacher appartiene alla stessa famiglia del modello student e può riprodurne blind spot; validatori deterministici e review umana restano obbligatori.

Le fonti ufficiali sono state ricontrollate il 2026-08-06. I Gemma Terms of Use risultano modificati il 2026-04-01, includono Gemma 3 nell’appendice, definiscono come Model Derivative un modello creato trasferendo pattern dagli output sintetici e dichiarano che Google non rivendica diritti sugli output. Il Gemma Prohibited Use Policy vieta, fra l’altro, pratica finanziaria non autorizzata, decisioni finanziarie automatizzate e affermazioni ingannevoli di competenza. Questa è una registrazione ingegneristica, non una consulenza legale; distribuzione commerciale o servizio customer-facing richiedono ancora revisione autorizzata.

## Scope

### Teacher e ambiente

- Usare RunPod Secure Cloud per l’inferenza esterna.
- Usare `google/gemma-3-27b-it`, risolvendo e registrando una revisione immutabile prima del primo download.
- Usare inizialmente una singola GPU NVIDIA con almeno 80 GB di VRAM per BF16, preferendo H100 80 GB o A100 80 GB in base a disponibilità e costo osservati; ogni fallback hardware richiede registrazione.
- Registrare immagine/digest, GPU, driver, CUDA, Python, PyTorch, Transformers, precisione, revisioni, spazio, RAM, tempo e memoria massima.
- Conservare `HF_TOKEN` esclusivamente come secret RunPod; nessun token deve comparire in file, history, log o manifest.

### Contratti e prompt

- Definire un prompt teacher versionato che includa system prompt VIS, input JSON, schema output, regole finanziarie e richiesta di un solo oggetto JSON.
- Definire un prompt critic distinto e versionato. Il critic riceve scenario, candidato e regole; produce una review separata e non riscrive mai il candidato.
- Fissare schema e formato versionato di candidato, review critic, run manifest, stato resumable e report riepilogativo.
- Registrare provenienza completa: scenario, candidate index, provider, modello, revisione, prompt/hash, parametri, timestamp, license review ID, hardware/run ID e token/costi quando disponibili.

### Generazione candidati

- Produrre due candidati indipendenti per ogni scenario TRAIN-04.
- Conservare il primo output di ogni `(scenarioId, candidateIndex)` senza retry che lo sostituisca.
- Usare decoding predefinito documentato. I due candidati possono usare seed derivati distinti, ma modello, prompt e parametri restano confrontabili.
- Supportare resume idempotente e rifiutare duplicati o manifest incompatibili.
- Conservare output grezzo, output parsato, errori di generazione/parsing, token e latenza.
- Nessun nome o dato di società reale viene introdotto; il teacher riceve solo scenari sintetici TRAIN-04.

### Validazione strutturale e semantica

- Applicare parser JSON stretto e schema TRAIN-01 a ogni candidato.
- Applicare le regole TRAIN-02 su evidence fields, dati insufficienti/stale/inconsistenti, strong decline, numeri non supportati, Markdown e buy/sell/hold.
- Aggiungere controlli mirati alle failure TRAIN-03: direzione di overvaluation, value trap, payout oltre 100%, priorità dei dati stale, soglie qualitative non fornite e adversarial review.
- Marcare ogni candidato con stato esplicito (`GENERATED`, `GENERATION_FAILED`, `PARSE_REJECTED`, `STRUCTURAL_REJECTED`, `SEMANTIC_REJECTED`, `CRITIC_PENDING`, `CRITIC_REVIEWED`).
- Non cancellare o riscrivere candidati falliti; i report devono contabilizzare ogni tentativo.

### Critic indipendente

- Eseguire un secondo passaggio dello stesso checkpoint 27B con prompt critic differente e contesto separato.
- Il critic valuta claim non supportati, numeri inventati, classificazione incoerente, rischi omessi, confidenza eccessiva, evidenze insufficienti, soglie inventate e linguaggio prescrittivo.
- La review critic contiene verdetto, punteggi, error codes, motivazioni brevi ed evidenze collegate; non contiene una risposta sostitutiva.
- Il critic viene eseguito soltanto su candidati parseable; gli altri conservano il motivo di esclusione.
- Nessun verdetto critic promuove automaticamente il candidato nel dataset training.

### Gate operativo in tre stadi

1. **Tooling locale:** backend fake deterministico, fixture di successo/failure, validatori, resume, report, cost accounting e test senza rete/GPU.
2. **Smoke RunPod:** 20 scenari stratificati sulle 14 categorie, due candidati ciascuno e critic separato. Misurare 40 generazioni candidate più review parseable, VRAM, latenza, token e costo.
3. **Bulk canonico:** 500 scenari e 1.000 candidati, autorizzato soltanto dopo report smoke e nuova approvazione esplicita dell’utente su hardware, durata e costo stimato.

### Review umana e report

- Preparare un campione deterministico di almeno 30 candidati che copra tutte le categorie, output accettabili, rejection e principali failure mode.
- Registrare alias, data, punteggi, note e decisione umana senza sostituirla con un modello.
- Produrre report globali e per categoria su generazione, parsing, schema, semantica, critic, token, latenza e costo.
- Conservare raw/run state in percorsi ignorati; promuovere in Git soltanto report e manifest compatti, sanitizzati e intenzionali.

## Decisions

1. **Teacher iniziale:** `google/gemma-3-27b-it`, revision-pinned prima dell’uso.
2. **Provider:** RunPod Secure Cloud self-hosted; nessun provider API proprietario è autorizzato.
3. **Precisione iniziale:** BF16 su singola GPU da almeno 80 GB per misurare il checkpoint senza quantizzazione; alternative richiedono decisione documentata.
4. **Due candidati per scenario:** 1.000 candidati canonici limitano il costo rispetto a tre mantenendo diversità sufficiente per TRAIN-06.
5. **Critic stesso checkpoint, prompt indipendente:** limita nuovi termini/provider ma non elimina il rischio di bias condiviso; la review umana resta obbligatoria.
6. **Primo output immutabile:** nessun retry opportunistico può migliorare retroattivamente le metriche.
7. **Separazione candidate/review:** il critic valuta, non corregge; candidato e review sono artifact indipendenti collegati da ID.
8. **Autorizzazione economica separata:** implementazione locale e smoke non autorizzano il bulk; il report smoke precede una nuova decisione dell’utente.
9. **License review ID:** ogni run usa un identificatore che rimanda alla revisione delle fonti ufficiali e alla data di accesso; un cambio dei termini blocca la produzione.
10. **Decision-support boundary:** nessun output può essere presentato come consulenza, decisione automatica o istruzione buy/sell/hold.

## Out of Scope

- Curazione finale, deduplica di qualità e costruzione del dataset accepted (TRAIN-06).
- Fine-tuning, QLoRA o adapter (TRAIN-07/08).
- Confronto student-adapter (TRAIN-09).
- Uso di aziende, filing, quotazioni o provider finanziari reali.
- Teacher proprietari hosted o fallback 12B senza aggiornamento ADR.
- Promozione automatica di candidati o review nel dataset di training.
- Runtime VIS, API, database, frontend o deployment customer-facing.
- Distribuzione commerciale del modello derivato.

## Compatibility and Risks

- Il 27B può richiedere più di una GPU o configurazioni diverse se il runtime BF16 supera 80 GB; lo smoke deve fermarsi prima del bulk e documentare il fallback.
- Due passaggi dello stesso modello possono condividere errori: deterministic validator e review umana sono gate indipendenti.
- I costi dipendono da prezzo RunPod, throughput, lunghezza output e quota di candidati parseable; nessuna stima viene fissata prima dello smoke.
- Termini e model card possono cambiare. La pipeline deve rifiutare manifest senza revisione modello e license review ID corrente.
- Output e log possono contenere testo avversariale sintetico; sanitizzazione e separazione secrets restano obbligatorie.
- I raw candidate possono essere grandi e non adatti a Git. Retention, checksum, trasferimento e distruzione risorse devono essere documentati prima della run.
- La pipeline non autorizza l’esercizio di professioni finanziarie né decisioni automatizzate; resta uno strumento di ricerca e preparazione dati.
