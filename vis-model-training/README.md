# VIS Gemma Training

Specifica incrementale per addestrare **Gemma 3** come *Investment Thesis Agent* di **ValueInvestingSupport (VIS)** mediante **Supervised Fine-Tuning (SFT)** e **QLoRA**.

> Stato: TRAIN-00 completo con GO condizionato; TRAIN-01 esplorativo/incompleto (3 casi); TRAIN-02 esplorativo/prototipale
> Versione: 1.2.0
> Modello target iniziale: `google/gemma-3-4b-it`
> Obiettivo: adapter LoRA specializzato, valutato e riproducibile
> Ambito: training del modello; l'integrazione runtime con VIS è esclusa
> Licenze e governance: registrate in TRAIN-00; revisione legale esterna ancora obbligatoria prima di distribuzione commerciale o servizio customer-facing

---

## 1. Scopo

Il progetto costruisce un modello locale capace di trasformare dati finanziari già calcolati da VIS in una tesi d'investimento strutturata, concisa e verificabile.

Il modello deve:

- interpretare esclusivamente le evidenze ricevute;
- separare attrattività della valutazione e qualità dell'impresa;
- costruire `bullCase`, `bearCase`, rischi e condizioni di invalidazione;
- collegare ogni affermazione ai campi di input;
- produrre JSON conforme allo schema;
- riconoscere dati insufficienti, incoerenti o obsoleti;
- richiedere revisione umana quando necessario.

Il modello non deve:

- calcolare DCF, Graham Number, DDM, Margin of Safety o Value Score;
- recuperare autonomamente dati finanziari;
- inventare numeri, eventi o caratteristiche aziendali;
- emettere istruzioni `BUY`, `SELL` o `HOLD`;
- usare conoscenza esterna non presente nell'input;
- sostituire i controlli deterministici di VIS.

---

## 2. Principio architetturale

```text
VIS deterministic engines
        |
        v
Financial context JSON
        |
        v
Gemma 3 + VIS LoRA adapter
        |
        v
Investment thesis JSON
        |
        v
Schema validator + semantic validators
```

I calcoli restano deterministici. Il modello si occupa di interpretazione, sintesi e organizzazione delle evidenze.

---

## 3. Strategia di sviluppo

Il progetto segue quattro regole:

1. **Benchmark before training**
   Nessun fine tuning viene considerato utile senza un confronto con il modello base.

2. **Dataset before infrastructure**
   La qualità dei dati viene affrontata prima dell'ottimizzazione GPU.

3. **Small experiments first**
   Il primo training usa poche centinaia di esempi e una sola configurazione.

4. **Promotion by evidence**
   Un adapter viene promosso solo se migliora metriche prestabilite senza aumentare allucinazioni o violazioni dello schema.

---

## 4. Roadmap

| Fase | Nome | Deliverable principale | Stato |
|---|---|---|---|
| TRAIN-00 | Decisioni e prerequisiti | ADR, governance e verifica hardware | Completo — GO condizionato |
| TRAIN-01 | Contratto del task | Schemi, prompt e casi seed | Esplorativo/incompleto — contratti pronti; 3 casi su 10 |
| TRAIN-02 | Validator del dataset | CLI di validazione | Esplorativo/prototipale — validatore Node senza suite completa |
| TRAIN-03 | Benchmark del modello base | Baseline riproducibile | Da avviare |
| TRAIN-04 | Generatore di scenari | Catalogo di scenari sintetici | Da avviare |
| TRAIN-05 | Teacher pipeline | Candidati generati dal modello teacher | Da avviare |
| TRAIN-06 | Curazione del dataset | Dataset accettato e versionato | Da avviare |
| TRAIN-07 | Ambiente QLoRA | Ambiente riproducibile e smoke test | Da avviare |
| TRAIN-08 | Training pilota | Primo adapter LoRA | Da avviare |
| TRAIN-09 | Valutazione comparativa | Report base vs adapter | Da avviare |
| TRAIN-10 | Iterazione dati e training | Adapter candidato | Da avviare |
| TRAIN-11 | Packaging | Artefatti, model card e release | Da avviare |
| TRAIN-12 | Handoff a VIS | Contratto per integrazione futura | Dettagliata (esplicitata in v1.1.0) |

Ogni fase deve essere completata prima di iniziare quella successiva, salvo attività esplorative esplicitamente marcate. I contratti TRAIN-01 e il prototipo TRAIN-02 creati prima della chiusura di TRAIN-00 sono classificati come esplorativi e non costituiscono completamento delle rispettive fasi.

---

# TRAIN-00 — Decisioni e prerequisiti

## Obiettivo

Verificare che il progetto sia tecnicamente e legalmente avviabile, senza ancora creare dataset o avviare training.

## Attività

### TRAIN-00.1 — Scelta del modello

Scelta iniziale:

```text
google/gemma-3-4b-it
```

Motivazioni:

- instruction-tuned;
- dimensione gestibile in locale;
- qualità attesa superiore a un modello 1B per un task finanziario strutturato;
- possibilità di usare PEFT/QLoRA.

La scelta va registrata in:

```text
docs/adr/ADR-001-model-selection.md
```

Stato: completato. Il modello student resta `google/gemma-3-4b-it`; il teacher iniziale selezionato è `google/gemma-3-27b-it`. Entrambi devono essere fissati a una revisione immutabile prima del primo utilizzo.

### TRAIN-00.2 — Verifica licenze e termini

Registrare:

- licenza e condizioni di Gemma;
- licenza di dataset di terze parti;
- condizioni del teacher model;
- possibilità o meno di usare gli output del teacher per il training;
- restrizioni per distribuzione commerciale;
- origine di ogni esempio.

File:

```text
docs/governance/data-and-model-licenses.md
```

> **Esito verifica**: completato come inventario di conformità ingegneristica in `docs/governance/data-and-model-licenses.md`. Gli output sintetici Gemma usati per il training producono un Model Derivative soggetto ai termini e agli obblighi di distribuzione applicabili. È richiesta revisione legale esterna prima di distribuzione commerciale o servizio customer-facing.

### TRAIN-00.3 — Verifica hardware

Raccogliere:

```text
GPU
VRAM
RAM
spazio disco
sistema operativo
driver NVIDIA
versione CUDA visibile da PyTorch
```

Comando minimo:

```bash
nvidia-smi
```

Il training QLoRA va inizialmente progettato per una singola GPU. Se la macchina locale non è sufficiente, mantenere invariato il repository e usare temporaneamente un ambiente GPU esterno.

Stato: completato in `docs/hardware/local-environment.md`. Il Mac Apple M4 locale non dispone di NVIDIA/CUDA ed è destinato a sviluppo e validazione; il workflow CUDA/QLoRA userà un ambiente GPU esterno, la cui fattibilità effettiva resta un gate misurato di TRAIN-07.

### TRAIN-00.4 — Politica dei dati

Gli esempi non devono contenere:

- dati personali;
- credenziali o token;
- dati finanziari proprietari non autorizzati;
- lunghi testi coperti da copyright;
- output teacher non consentiti dal contratto;
- informazioni non tracciabili.

La politica completa, inclusi provenienza, review state, retention ed escalation, è registrata in `docs/governance/data-policy.md`.

## Deliverable

```text
docs/adr/ADR-001-model-selection.md
docs/governance/data-and-model-licenses.md
docs/governance/data-policy.md
docs/governance/secret-hygiene.md
docs/hardware/local-environment.md
```

## Criteri di accettazione

- [x] modello target scelto;
- [x] licenza del modello registrata;
- [x] teacher autorizzato per la successiva valutazione TRAIN-05 (`google/gemma-3-27b-it`);
- [x] hardware documentato;
- [x] nessun segreto rilevato nei file del sottoprogetto coperti dai controlli TRAIN-00;
- [x] decisione `GO` o `NO-GO` esplicita (GO).

Il GO è condizionato: non autorizza ancora download, training, distribuzione commerciale o servizio customer-facing. Restano obbligatori i gate delle fasi successive e la revisione legale esterna prevista dal registro licenze.

---

# TRAIN-01 — Contratto del task

## Obiettivo

Definire input, output, prompt e comportamento atteso prima di produrre molti esempi.

## Struttura attualmente implementata

```text
vis-model-training/
├── schemas/
│   ├── thesis-input.schema.json
│   └── thesis-output.schema.json
├── prompts/
│   └── system-prompt-v1.txt
├── examples/
│   ├── example-001.json
│   ├── example-002.json
│   └── example-003.json
└── datasets/
    └── seed-dataset-v1.jsonl
```

## Input minimo

```json
{
  "symbol": "VIS001",
  "analysisDate": "2026-08-01",
  "marketPrice": 80.0,
  "intrinsicValue": 110.0,
  "marginOfSafetyPercent": 27.27,
  "valueScore": 82.0,
  "dividendYieldPercent": 3.2,
  "payoutRatioPercent": 45.0,
  "netDebtToEbitda": 1.1,
  "revenueTrend": "GROWING",
  "earningsTrend": "GROWING",
  "freeCashFlowTrend": "STABLE",
  "dataQuality": "COMPLETE",
  "deterministicWarnings": []
}
```

## Output minimo

```json
{
  "classification": "POTENTIALLY_UNDERVALUED",
  "confidence": 0.88,
  "summary": "Valuation indicators are favorable and operating trends are supportive.",
  "bullCase": [
    {
      "claim": "The market price is below the supplied intrinsic value.",
      "evidenceFields": [
        "marketPrice",
        "intrinsicValue",
        "marginOfSafetyPercent"
      ]
    }
  ],
  "bearCase": [],
  "keyRisks": [],
  "keyAssumptions": [
    "The supplied intrinsic value remains valid under the VIS assumptions."
  ],
  "invalidationConditions": [
    "A material deterioration in free cash flow."
  ],
  "dataWarnings": [],
  "humanReviewRequired": false
}
```

## Classificazioni ammesse

```text
POTENTIALLY_UNDERVALUED
FAIRLY_VALUED
POTENTIALLY_OVERVALUED
UNDER_REVIEW
INSUFFICIENT_DATA
```

## Prompt di sistema

Il prompt deve imporre almeno:

1. uso esclusivo dell'input;
2. divieto di inventare dati;
3. divieto di ricalcolare indicatori;
4. evidenze obbligatorie per ogni claim;
5. separazione tra prezzo e qualità;
6. JSON senza Markdown;
7. revisione umana per dati dubbi;
8. nessuna raccomandazione operativa.

## Esempi seed

Il target di completamento è di almeno 10 casi manuali:

1. impresa solida e sottovalutata;
2. value trap;
3. impresa solida ma sopravvalutata;
4. fair value;
5. dividendo elevato ma insostenibile;
6. leva finanziaria elevata;
7. FCF negativo o in deterioramento;
8. indicatori contraddittori;
9. dati obsoleti;
10. dati insufficienti.

Usare inizialmente simboli sintetici (`VIS001`, `VIS002`) per impedire al modello di ricorrere a conoscenze pregresse.

Stato attuale: sono implementati 3 casi sintetici (`VIS1`, `VIS2`, `VIS3`): impresa solida e sottovalutata, potenziale value trap e dati insufficienti. Restano da aggiungere 7 scenari per soddisfare il target.

## Deliverable

```text
vis-model-training/schemas/thesis-input.schema.json
vis-model-training/schemas/thesis-output.schema.json
vis-model-training/prompts/system-prompt-v1.txt
vis-model-training/examples/example-001.json
vis-model-training/examples/example-002.json
vis-model-training/examples/example-003.json
vis-model-training/datasets/seed-dataset-v1.jsonl
```

## Criteri di accettazione

- [x] schemi validi;
- [ ] 10 casi manuali (3 implementati);
- [x] tutti gli output attualmente presenti conformi;
- [x] nessuna informazione esterna nei casi attualmente presenti;
- [x] ogni claim attualmente presente ha almeno un'evidenza;
- [x] ogni scenario attualmente presente appartiene a una categoria dichiarata.

---

# TRAIN-02 — Validator del dataset

## Obiettivo

Impedire che esempi formalmente o semanticamente errati entrino nel dataset.

## Stato attuale

È presente `vis-model-training/scripts/validate-dataset.mjs`, un validatore prototipale basato esclusivamente sui moduli integrati di Node.js. Verifica i due contratti, i JSON incorporati, i riferimenti `evidenceFields`, i tre scenari seed e la corrispondenza esatta fra esempi e JSONL.

Non sono ancora implementati la struttura Python prevista sotto `src/vis_training/`, la CLI parametrica, i codici errore stabili, i report JSON/testuali e la suite di almeno 15 test. TRAIN-02 non è quindi completato.

## Struttura

```text
src/vis_training/
  validation/
    schema_validator.py
    semantic_validator.py
    dataset_validator.py
tests/
  validation/
scripts/
  validate_dataset.py
```

## Validazioni strutturali

- JSONL leggibile;
- una struttura JSON per riga;
- `messages` presente;
- ruoli nell'ordine previsto;
- input conforme allo schema;
- output conforme allo schema;
- metadata obbligatori;
- identificatore univoco.

## Validazioni semantiche

- `evidenceFields` presenti nell'input;
- nessun riferimento a campi nulli senza warning;
- `INSUFFICIENT_DATA` quando `dataQuality=INSUFFICIENT`;
- `humanReviewRequired=true` per dati insufficienti, incoerenti o obsoleti;
- presenza di `bearCase` per trend fortemente negativi;
- divieto di `BUY`, `SELL`, `HOLD`;
- numeri nell'output presenti anche nell'input, salvo `confidence`;
- confidenza compresa tra 0 e 1;
- assenza di Markdown;
- assenza di testo fuori dal JSON.

## CLI

```bash
python -m scripts.validate_dataset \
  --dataset datasets/seed/seed-dataset-v1.jsonl \
  --input-schema schemas/thesis-input.schema.json \
  --output-schema schemas/thesis-output.schema.json
```

## Output atteso

```text
records: 10
valid: 10
invalid: 0
warnings: 0
```

## Deliverable

- validator;
- test automatici;
- report JSON e testuale;
- codici errore stabili.

## Criteri di accettazione

- [ ] almeno 15 test;
- [x] dataset seed attuale valido al 100%;
- [ ] un dataset volutamente corrotto viene rifiutato;
- [x] uscita non zero in caso di errore rilevato dal prototipo;
- [ ] report utilizzabile in CI.

---

# TRAIN-03 — Benchmark del modello base

## Obiettivo

Misurare Gemma 3 prima del fine tuning.

## Motivazione

Senza baseline non è possibile stabilire se QLoRA abbia prodotto un miglioramento reale.

## Dataset benchmark

Creare almeno 50 casi, separati dal training:

```text
datasets/benchmark/base-benchmark-v1.jsonl
```

Distribuzione minima:

| Categoria | Casi |
|---|---:|
| Sottovalutazione robusta | 7 |
| Value trap | 7 |
| Sopravvalutazione | 6 |
| Fair value | 5 |
| Dividendo a rischio | 5 |
| Dati insufficienti | 5 |
| Dati obsoleti | 5 |
| Contraddizioni | 5 |
| Casi avversariali | 5 |

## Inferenza deterministica

Configurazione iniziale suggerita:

```yaml
temperature: 0.0
do_sample: false
max_new_tokens: 1024
```

Registrare:

- modello;
- revision;
- tokenizer;
- prompt version;
- parametri;
- hardware;
- tempo per caso;
- output grezzo;
- output parsato;
- errori.

## Metriche

### Metriche automatiche

- JSON validity rate;
- schema compliance rate;
- classification accuracy;
- evidence-field precision;
- unsupported numeric claim rate;
- prohibited recommendation rate;
- human-review accuracy;
- exact field coverage;
- average output length;
- latency.

### Metriche manuali

Campione minimo di 20 casi:

- correttezza della sintesi;
- equilibrio bull/bear;
- qualità dei rischi;
- aderenza all'input;
- utilità per un revisore umano.

## Deliverable

```text
reports/baseline/gemma-3-4b-it-v1/
```

## Gate

Il training non inizia finché:

- [ ] benchmark completato;
- [ ] errori principali classificati;
- [ ] metriche salvate;
- [ ] almeno tre comportamenti da migliorare sono espliciti.

---

# TRAIN-04 — Generatore di scenari finanziari

## Obiettivo

Creare input sintetici controllati senza dipendere da aziende reali.

## Principio

Lo scenario viene generato da regole; il teacher produce solo l'output atteso.

## Catalogo scenari

Creare una tassonomia:

```text
UNDERVALUED_STRONG
UNDERVALUED_WEAK
VALUE_TRAP
OVERVALUED_STRONG
FAIR_VALUE
DIVIDEND_SAFE
DIVIDEND_RISK
HIGH_LEVERAGE
FCF_DETERIORATION
CONTRADICTORY_SIGNALS
STALE_DATA
INSUFFICIENT_DATA
INCONSISTENT_DATA
ADVERSARIAL_INPUT
```

## Regole di coerenza

Esempi:

- `marketPrice > 0`;
- `intrinsicValue >= 0` o `null`;
- margin of safety coerente con prezzo e valore, salvo scenario di inconsistenza;
- value score tra 0 e 100;
- payout negativo ammesso solo quando semanticamente giustificato;
- trend selezionati da enum;
- warning coerenti con lo scenario;
- seed casuale registrato.

## Output del generatore

```json
{
  "scenarioId": "SCN-000001",
  "scenarioType": "VALUE_TRAP",
  "generatorVersion": "1.0.0",
  "seed": 44291,
  "input": {}
}
```

## Quantità iniziale

Generare 500 scenari candidati:

- 60% casi ordinari;
- 25% casi difficili;
- 15% casi avversariali o incompleti.

## Deliverable

```text
src/vis_training/scenarios/
datasets/candidates/scenarios-v1.jsonl
reports/scenarios/distribution-v1.json
```

## Criteri di accettazione

- [ ] generazione riproducibile tramite seed;
- [ ] nessuna incoerenza non intenzionale;
- [ ] distribuzione documentata;
- [ ] almeno 14 categorie;
- [ ] test sulle soglie e sui casi limite.

---

# TRAIN-05 — Teacher pipeline

## Obiettivo

Usare un modello più potente per generare risposte candidate.

## Prerequisito legale

Il teacher deve avere condizioni d'uso compatibili con:

- generazione di dati sintetici;
- utilizzo degli output per addestrare Gemma;
- eventuale uso commerciale;
- conservazione degli output.

In assenza di chiarezza, la pipeline non deve essere usata.

## Flusso

```text
scenario
  -> teacher prompt
  -> candidate A
  -> candidate B
  -> candidate C
  -> structural validation
  -> semantic validation
  -> critic review
  -> accepted/rejected
```

## Numero di candidati

Generare 2-3 risposte per scenario, non una sola.

## Prompt teacher

Il teacher deve ricevere:

- system prompt VIS;
- input JSON;
- output schema;
- elenco delle regole;
- richiesta di produrre solo JSON.

Non deve ricevere l'identità di società reali nella prima iterazione.

## Critic

Un secondo passaggio valuta:

- unsupported claims;
- valori inventati;
- classificazione incoerente;
- omissione di rischi;
- eccesso di confidenza;
- evidenze insufficienti;
- linguaggio prescrittivo.

Il critic non modifica automaticamente il record originale. Produce una valutazione separata.

## Provenienza

Ogni candidato deve contenere:

```json
{
  "source": "SYNTHETIC_TEACHER",
  "teacherProvider": "...",
  "teacherModel": "...",
  "teacherModelVersion": "...",
  "promptVersion": "...",
  "generationParameters": {},
  "generatedAt": "...",
  "licenseReviewId": "..."
}
```

## Deliverable

```text
datasets/candidates/teacher-candidates-v1.jsonl
datasets/reviews/critic-reviews-v1.jsonl
reports/teacher/generation-summary-v1.json
```

## Criteri di accettazione

- [ ] provenienza completa;
- [ ] nessun record senza modello e prompt versionati;
- [ ] tutti i candidati passano il parser JSON oppure sono marcati come scartati;
- [ ] costi e token registrati, quando applicabili;
- [ ] output teacher mai copiato direttamente nel training senza validazione.

---

# TRAIN-06 — Curazione del dataset

## Obiettivo

Trasformare i candidati in un dataset di qualità.

## Pipeline di accettazione

```text
candidate
  -> schema validation
  -> semantic validation
  -> deterministic checks
  -> deduplication
  -> quality scoring
  -> human sampling
  -> accepted dataset
```

## Quality score

Esempio:

| Componente | Peso |
|---|---:|
| Validità schema | gate |
| Correttezza classificazione | 25 |
| Evidence grounding | 25 |
| Assenza di invenzioni | 25 |
| Completezza rischi | 10 |
| Coerenza confidence | 5 |
| Chiarezza | 5 |
| Concisione | 5 |

Soglia iniziale:

```text
qualityScore >= 85
```

La soglia non sostituisce i gate obbligatori.

## Deduplicazione

Controllare:

- input identici;
- input quasi identici;
- output template ripetuti;
- eccessiva concentrazione su una categoria;
- esempi con stessa struttura e soli numeri modificati.

## Revisione umana

Revisionare almeno:

- 100% dei casi ad alto rischio;
- 100% dei casi avversariali iniziali;
- 20% dei casi ordinari;
- tutti gli esempi contestati dal validator;
- tutti i casi con confidence superiore a 0.9.

## Split

Lo split deve avvenire per famiglia di scenario, prima del training finale:

```text
train       80%
validation  10%
test        10%
```

Scenari quasi duplicati devono rimanere nello stesso split.

## Dimensione obiettivo della prima iterazione

```text
train:       600-1.000
validation:   75-125
test:         75-125
```

## Deliverable

```text
datasets/releases/v1/train.jsonl
datasets/releases/v1/validation.jsonl
datasets/releases/v1/test.jsonl
datasets/releases/v1/dataset-card.md
datasets/releases/v1/manifest.json
```

## Criteri di accettazione

- [ ] 100% schema compliant;
- [ ] 0 unsupported numeric claims noti;
- [ ] distribuzione categorie documentata;
- [ ] duplicati sotto la soglia definita;
- [ ] campione umano approvato;
- [ ] hash dei file nel manifest;
- [ ] test set congelato.

---

# TRAIN-07 — Ambiente QLoRA

## Obiettivo

Creare un ambiente riproducibile e verificare che Gemma sia caricabile in 4 bit con adapter LoRA.

## Struttura

```text
pyproject.toml
requirements.lock
configs/
  qlora-smoke.yaml
  qlora-pilot.yaml
src/vis_training/training/
scripts/
  check_environment.py
  smoke_train.py
```

## Dipendenze principali

```text
torch
transformers
datasets
trl
peft
accelerate
bitsandbytes
jsonschema
pydantic
pytest
```

Non fissare versioni nel README. Bloccarle nel lock file dopo un test riuscito.

## Verifica ambiente

Lo script deve stampare:

- Python;
- PyTorch;
- CUDA;
- GPU;
- VRAM;
- Transformers;
- TRL;
- PEFT;
- bitsandbytes;
- supporto BF16;
- modello e tokenizer caricati.

## Configurazione QLoRA iniziale

```yaml
model_name: google/gemma-3-4b-it
load_in_4bit: true
bnb_4bit_quant_type: nf4
bnb_4bit_use_double_quant: true
compute_dtype: bfloat16-or-float16
lora_r: 16
lora_alpha: 32
lora_dropout: 0.05
bias: none
task_type: CAUSAL_LM
```

I `target_modules` devono essere verificati sul modello effettivamente caricato, non copiati senza controllo da tutorial relativi ad altre architetture.

## Smoke training

Usare 8-16 esempi per:

- caricamento;
- tokenizzazione;
- forward pass;
- backward pass;
- salvataggio adapter;
- reload adapter;
- inferenza.

## Criteri di accettazione

- [ ] ambiente ricreabile;
- [ ] modello caricato in 4 bit;
- [ ] soli parametri LoRA addestrabili;
- [ ] almeno uno step completato;
- [ ] adapter salvato e ricaricato;
- [ ] nessun errore NaN/Inf;
- [ ] utilizzo VRAM registrato.

---

# TRAIN-08 — Training pilota

## Obiettivo

Eseguire il primo training reale su un sottoinsieme controllato.

## Dataset

Usare 200-300 esempi bilanciati. Non usare ancora tutto il dataset.

## Configurazione iniziale indicativa

```yaml
num_train_epochs: 2
per_device_train_batch_size: 1
gradient_accumulation_steps: 8
learning_rate: 0.0002
lr_scheduler_type: cosine
warmup_ratio: 0.05
weight_decay: 0.0
max_grad_norm: 1.0
logging_steps: 5
eval_strategy: steps
eval_steps: 25
save_steps: 25
save_total_limit: 2
gradient_checkpointing: true
packing: false
seed: 42
max_length: value-determined-by-token-analysis
```

Questi valori sono ipotesi iniziali, non requisiti immutabili.

## Token analysis

Prima del training calcolare:

- min;
- mediana;
- p90;
- p95;
- massimo;
- percentuale di truncation.

Scegliere `max_length` in base ai dati, non arbitrariamente.

## Loss masking

Preferire l'addestramento sulla risposta dell'assistente, evitando di calcolare loss sul prompt quando la configurazione e il template lo consentono.

## Tracking

Registrare:

- configurazione completa;
- commit Git;
- dataset hash;
- modello revision;
- random seed;
- training loss;
- validation loss;
- learning rate;
- durata;
- VRAM massima;
- checkpoint;
- errori.

## Deliverable

```text
artifacts/adapters/pilot-v1/
runs/pilot-v1/
reports/training/pilot-v1.md
```

## Criteri di accettazione

- [ ] training completato;
- [ ] adapter ricaricabile;
- [ ] loss finita;
- [ ] nessuna evidente divergenza;
- [ ] inferenza su 10 casi;
- [ ] artefatti riproducibili.

---

# TRAIN-09 — Valutazione comparativa

## Obiettivo

Confrontare modello base e adapter sullo stesso test set congelato.

## Varianti

```text
A: Gemma base + system prompt
B: Gemma base + few-shot prompt
C: Gemma + VIS adapter + system prompt
```

## Metriche gate

Esempio di soglie iniziali:

| Metrica | Gate candidato |
|---|---:|
| JSON validity | >= 99% |
| Schema compliance | >= 98% |
| Classification accuracy | >= baseline + 10 punti |
| Unsupported numeric claims | <= 1% |
| Evidence-field precision | >= 98% |
| Prohibited recommendation | 0% |
| Insufficient-data recall | >= 95% |

Le soglie vanno adattate dopo la baseline, ma devono essere definite prima di leggere i risultati finali.

## Analisi errori

Ogni errore deve essere classificato:

```text
FORMAT_ERROR
SCHEMA_ERROR
CLASSIFICATION_ERROR
UNSUPPORTED_CLAIM
UNSUPPORTED_NUMBER
MISSING_RISK
MISSING_EVIDENCE
OVERCONFIDENCE
UNDERCONFIDENCE
KNOWLEDGE_LEAKAGE
PROHIBITED_ADVICE
EXCESSIVE_VERBOSITY
```

## Revisione umana cieca

Su almeno 30 casi, il revisore non deve sapere quale variante abbia prodotto la risposta.

Valutare da 1 a 5:

- correttezza;
- grounding;
- completezza;
- chiarezza;
- utilità;
- prudenza.

## Decisione

Possibili esiti:

```text
PROMOTE
ITERATE_DATA
ITERATE_TRAINING
REJECT
```

## Deliverable

```text
reports/evaluation/pilot-v1/
```

## Criteri di accettazione

- [ ] stesso test set per tutte le varianti;
- [ ] metriche automatiche disponibili;
- [ ] revisione umana completata;
- [ ] error taxonomy popolata;
- [ ] decisione documentata.

---

# TRAIN-10 — Iterazione dati e training completo

## Obiettivo

Migliorare il dataset sulla base degli errori reali, non aggiungendo dati indiscriminatamente.

## Loop

```text
evaluation errors
  -> select error clusters
  -> create targeted scenarios
  -> teacher generation
  -> validation
  -> human review
  -> dataset v2
  -> training v2
  -> frozen benchmark
```

## Regole

- non modificare il test set per favorire il modello;
- creare un nuovo test set solo per nuove capacità;
- mantenere separati dataset v1 e v2;
- confrontare più learning rate prima di aumentare rank LoRA;
- aumentare `r` solo con una motivazione;
- evitare molti esperimenti simultanei;
- cambiare una variabile principale per run.

## Esperimenti suggeriti

1. learning rate;
2. numero epoche;
3. LoRA rank;
4. target modules;
5. prompt version;
6. proporzione casi difficili;
7. response-only loss;
8. packing.

## Early stopping concettuale

Interrompere o non promuovere se:

- validation loss peggiora stabilmente;
- aumenta la conoscenza inventata;
- peggiora il formato JSON;
- il modello diventa eccessivamente sicuro;
- impara frasi rigide senza generalizzare.

## Deliverable

```text
datasets/releases/v2/
artifacts/adapters/candidate-v2/
reports/evaluation/candidate-v2/
```

## Criteri di accettazione

- [ ] miglioramento sui gate;
- [ ] nessuna regressione critica;
- [ ] dataset versionato;
- [ ] run riproducibile;
- [ ] adapter candidato selezionato.

---

# TRAIN-11 — Packaging e release

## Obiettivo

Produrre un artefatto utilizzabile e documentato.

## Artefatti

```text
adapter_model.safetensors
adapter_config.json
tokenizer files, se necessari
training_config.yaml
dataset_manifest.json
evaluation_report.json
README.md
MODEL_CARD.md
LICENSES.md
checksums.txt
```

## Model card

Deve descrivere:

- base model;
- task;
- lingue;
- dati di training;
- provenienza;
- metodologia;
- metriche;
- limiti;
- usi previsti;
- usi esclusi;
- hardware;
- rischi;
- necessità di validazione esterna;
- assenza di consulenza finanziaria.

## Versionamento

Esempio:

```text
vis-gemma-thesis-adapter-0.1.0
```

Regole:

- patch: correzioni packaging;
- minor: nuovo training compatibile;
- major: modifica del contratto input/output.

## Merge opzionale

Non fondere immediatamente l'adapter nel modello base. Mantenere prima:

```text
base model + adapter
```

Vantaggi:

- artefatto più piccolo;
- confronto più facile;
- rollback;
- gestione versioni;
- riuso del modello base.

## Criteri di accettazione

- [ ] model card completa;
- [ ] checksum;
- [ ] adapter ricaricabile da ambiente pulito;
- [ ] inferenza di accettazione;
- [ ] licenze incluse;
- [ ] report di benchmark collegato.

---

# TRAIN-12 — Handoff verso VIS

## Obiettivo

Preparare il modello all'integrazione, senza realizzarla in questo repository. Il risultato di questa fase è un contratto completo, testabile e versionato che il team VIS (Java/Spring Boot) può implementare senza dover aprire questo repository né conoscere i dettagli del training.

Questa fase **non produce codice di integrazione**: produce documentazione, schemi e una suite di casi di accettazione che qualunque futuro client (Java, Python, CLI) deve poter superare prima di essere collegato a un flusso reale.

## 12.1 — Contratto runtime (identità del modello)

Ogni release dell'adapter deve pubblicare un manifest immutabile:

```json
{
  "modelId": "vis-gemma-thesis-adapter",
  "modelVersion": "0.1.0",
  "baseModel": "google/gemma-3-4b-it",
  "baseModelRevision": "<commit hash o revision HF>",
  "adapterChecksum": "<sha256>",
  "promptVersion": "1.0.0",
  "inputSchemaVersion": "1.0.0",
  "outputSchemaVersion": "1.0.0",
  "trainingDatasetRelease": "v1",
  "trainingRunId": "<id run TRAIN-08/TRAIN-10>",
  "releasedAt": "2026-08-01T00:00:00Z",
  "status": "CANDIDATE"
}
```

`status` segue un ciclo esplicito: `CANDIDATE` → `APPROVED` → `DEPRECATED`. Solo un manifest `APPROVED` può essere collegato a un ambiente non locale. Nessun campo di questo manifest è modificabile dopo la pubblicazione: una correzione richiede una nuova versione, mai un edit in place.

## 12.2 — Contratto di richiesta/risposta

Il contratto runtime non è lo schema di training grezzo (TRAIN-01), ma un involucro (envelope) pensato per un client esterno, che deve poter distinguere un esito valido da un errore senza fare parsing euristico:

```json
// Request
{
  "requestId": "uuid",
  "modelVersion": "0.1.0",
  "input": { "...": "conforme a thesis-input.schema.json" }
}
```

```json
// Response — esito valido
{
  "requestId": "uuid",
  "status": "OK",
  "modelVersion": "0.1.0",
  "promptVersion": "1.0.0",
  "latencyMs": 842,
  "output": { "...": "conforme a thesis-output.schema.json" }
}
```

```json
// Response — esito di errore
{
  "requestId": "uuid",
  "status": "ERROR",
  "errorCode": "SCHEMA_VALIDATION_FAILED",
  "errorMessage": "output non conforme allo schema dopo retry",
  "rawOutputAvailable": true
}
```

`rawOutputAvailable` indica solo se l'output grezzo è stato conservato per audit (TRAIN-12.5); non viene mai restituito al chiamante come sostituto di un output valido.

### Codici di errore minimi

| Codice | Significato | Azione lato VIS |
|---|---|---|
| `SCHEMA_VALIDATION_FAILED` | output non parsabile o non conforme dopo i retry consentiti | fallback deterministico, non mostrare output parziale |
| `TIMEOUT` | superato il timeout configurato | retry con backoff, poi fallback |
| `INPUT_SCHEMA_INVALID` | input non conforme allo schema atteso da questa versione | errore 4xx verso il chiamante, nessun retry |
| `MODEL_VERSION_UNAVAILABLE` | versione richiesta non `APPROVED` o non caricata | errore di configurazione, non un caso a runtime |
| `HUMAN_REVIEW_REQUIRED` | non è un errore: l'output è valido ma `humanReviewRequired=true` | instradare a coda di revisione, mai auto-pubblicare |

## 12.3 — Requisiti di integrazione futura

**Inferenza**

- temperatura 0 e `do_sample=false` come default, coerente con TRAIN-03;
- `max_new_tokens` fissato e allineato al benchmark;
- timeout esplicito per chiamata, con valore diverso per ambienti batch vs interattivi;
- retry consentito **solo** per errori di formato (JSON non valido) o timeout di rete, mai per rigenerare un output "più convincente";
- numero massimo di retry configurabile, con fallback obbligatorio al superamento.

**Fallback**

- il fallback in caso di errore o superamento retry deve essere **deterministico**: nessuna chiamata a un secondo modello come ripiego silenzioso;
- il fallback minimo accettabile è: classificazione `UNDER_REVIEW`, `humanReviewRequired=true`, nessun bull/bear case generato, motivo dell'errore tracciato;
- il fallback non deve mai esporre l'utente finale a un JSON malformato o a testo libero non validato.

**Sicurezza e dati**

- nessun dato sensibile nei log (né input finanziari proprietari oltre quanto già pubblico, né chiavi, né PII);
- i log devono contenere `requestId`, `modelVersion`, `promptVersion`, esito, latenza — non l'intero payload in chiaro salvo ambiente di audit controllato;
- separazione tra log operativi (osservabilità) e log di audit (tracciabilità delle evidenze), con retention diverse.

**Audit e tracciabilità**

- ogni evidenza citata nell'output (`evidenceFields`) deve restare verificabile a posteriori contro l'input originale;
- ogni risposta deve essere riconducibile a `modelVersion` + `promptVersion` + `requestId`, per poter isolare regressioni per versione;
- i casi con `humanReviewRequired=true` devono finire in una coda tracciata, non essere semplicemente scartati o mostrati con un avviso ignorabile.

**Osservabilità**

- metriche minime da esporre lato VIS: tasso di errore per `errorCode`, latenza p50/p95, tasso di `humanReviewRequired`, tasso di fallback attivato;
- un incremento anomalo del tasso di fallback o di `humanReviewRequired` deve generare un alert, non passare inosservato: è il segnale più diretto di un drift tra i dati reali e la distribuzione del training.

## 12.4 — Suite di accettazione

Prima di collegare una versione `APPROVED` a un ambiente non locale, il client deve superare una suite di casi derivata da TRAIN-03 e TRAIN-04, non nuova:

- tutte le categorie del benchmark base (TRAIN-03) rieseguite contro l'adapter, non solo contro il modello base;
- tutti i 14+ scenari sintetici di TRAIN-04, incluso il sottoinsieme avversariale;
- test di contratto: input volutamente malformato → deve produrre `INPUT_SCHEMA_INVALID`, mai un tentativo di interpretazione;
- test di timeout simulato → deve attivare il fallback deterministico, non un errore non gestito;
- test di versione non `APPROVED` → chiamata deve essere rifiutata a livello di configurazione, non silenziosamente instradata alla versione precedente;
- test di non regressione: le metriche automatiche di TRAIN-03/TRAIN-09 non devono peggiorare tra una versione `APPROVED` e la successiva, altrimenti la promozione è bloccata.

## 12.5 — Politica di audit dei casi dubbi

Ogni record con `humanReviewRequired=true` o con `dataWarnings` non vuoto deve essere conservato (input, output, `modelVersion`) in uno storage separato dai log operativi, per un periodo minimo definito lato governance VIS. Questo storage è la base per:

- costruire nuovi scenari di TRAIN-04 dai casi reali problematici;
- misurare se il tasso di revisione umana è stabile o in aumento nel tempo;
- alimentare eventuali round successivi di TRAIN-06 (curazione) con esempi reali, non solo sintetici — sempre nel rispetto della politica dati di TRAIN-00.4.

## 12.6 — Versioning e deprecazione

- `modelVersion` segue semver: patch per correzioni di packaging, minor per training compatibile con lo stesso contratto input/output, major per modifica del contratto;
- una versione `major` nuova richiede che VIS aggiorni esplicitamente `inputSchemaVersion`/`outputSchemaVersion` lato client: non c'è compatibilità implicita tra major diverse;
- una versione può passare a `DEPRECATED` solo dopo che una versione successiva è `APPROVED` e ha superato la suite di accettazione; non si deprecano versioni senza sostituto pronto;
- rollback: tornare a una versione `APPROVED` precedente deve essere un cambio di configurazione (puntare a un altro adapter), mai un nuovo training d'urgenza.

## 12.7 — Responsabilità VIS / modello

| Ambito | Responsabilità |
|---|---|
| Calcolo indicatori finanziari (DCF, Value Score, Margin of Safety, ecc.) | VIS (deterministico, invariato) |
| Validazione schema input/output a runtime | VIS (lato client, oltre al validator del training) |
| Interpretazione, sintesi, bull/bear case | Modello |
| Decisione se pubblicare, mettere in coda di revisione o scartare un output | VIS |
| Qualunque raccomandazione operativa (BUY/SELL/HOLD) verso l'utente finale | Fuori scope per entrambi in questa fase — non previsto dal contratto |
| Log, audit, retention | VIS |
| Versionamento e promozione dell'adapter | Repository di training (questo progetto) |

## Deliverable

```text
docs/integration/runtime-contract.md
docs/integration/request-response-contract.md
docs/integration/error-codes.md
docs/integration/acceptance-suite.md
docs/integration/responsibility-matrix.md
docs/integration/versioning-policy.md
```

## Criteri di accettazione

- [ ] contratto runtime definito e immutabile per versione pubblicata;
- [ ] envelope di richiesta/risposta definito, incluso lo schema di errore;
- [ ] tabella dei codici di errore con azione lato VIS per ciascuno;
- [ ] suite di casi di accettazione derivata da TRAIN-03/TRAIN-04, non nuova;
- [ ] politica di fallback deterministico documentata e testata;
- [ ] politica di audit dei casi `humanReviewRequired`/`dataWarnings` definita, con retention;
- [ ] politica di versioning/deprecazione/rollback esplicita;
- [ ] versioni degli schemi tracciate per ogni release;
- [ ] responsabilità VIS/modello separate in una matrice esplicita;
- [ ] nessun requisito di questa fase implica modifiche agli algoritmi deterministici VIS.

---

## 5. Struttura finale del repository

```text
vis-model-training/
├── README.md
├── pyproject.toml
├── requirements.lock
├── configs/
│   ├── qlora-smoke.yaml
│   ├── qlora-pilot.yaml
│   └── evaluation.yaml
├── schemas/
│   ├── thesis-input.schema.json
│   └── thesis-output.schema.json
├── prompts/
│   ├── system-prompt-v1.txt
│   ├── teacher-prompt-v1.txt
│   └── critic-prompt-v1.txt
├── datasets/
│   ├── seed/
│   ├── candidates/
│   ├── reviews/
│   ├── benchmark/
│   └── releases/
├── src/
│   └── vis_training/
│       ├── scenarios/
│       ├── generation/
│       ├── validation/
│       ├── curation/
│       ├── training/
│       └── evaluation/
├── scripts/
│   ├── validate_dataset.py
│   ├── generate_scenarios.py
│   ├── generate_teacher_outputs.py
│   ├── build_dataset_release.py
│   ├── check_environment.py
│   ├── train_qlora.py
│   └── evaluate_model.py
├── tests/
├── reports/
├── artifacts/
├── runs/
└── docs/
    ├── adr/
    ├── governance/
    └── integration/
```

---

## 6. Formato dataset

Formato conversazionale JSONL:

```json
{
  "messages": [
    {
      "role": "system",
      "content": "..."
    },
    {
      "role": "user",
      "content": "{\"symbol\":\"VIS001\", ...}"
    },
    {
      "role": "assistant",
      "content": "{\"classification\":\"UNDER_REVIEW\", ...}"
    }
  ],
  "metadata": {
    "exampleId": "VIS-TRAIN-000001",
    "scenarioType": "VALUE_TRAP",
    "source": "SYNTHETIC_TEACHER",
    "datasetVersion": "1.0.0",
    "inputSchemaVersion": "1.0.0",
    "outputSchemaVersion": "1.0.0",
    "promptVersion": "1.0.0",
    "reviewStatus": "APPROVED"
  }
}
```

Regole:

- una riga per esempio;
- nessun array esterno;
- contenuti `user` e `assistant` come JSON serializzato;
- encoding UTF-8;
- newline finale;
- ID univoco;
- metadata obbligatori;
- nessun segreto;
- nessun record non validato nel dataset release.

---

## 7. Comandi previsti

```bash
# Validazione
python scripts/validate_dataset.py \
  --dataset datasets/releases/v1/train.jsonl

# Generazione scenari
python scripts/generate_scenarios.py \
  --config configs/scenarios-v1.yaml

# Verifica ambiente
python scripts/check_environment.py

# Smoke test
python scripts/train_qlora.py \
  --config configs/qlora-smoke.yaml

# Training pilota
python scripts/train_qlora.py \
  --config configs/qlora-pilot.yaml

# Valutazione
python scripts/evaluate_model.py \
  --base-model google/gemma-3-4b-it \
  --adapter artifacts/adapters/pilot-v1 \
  --dataset datasets/benchmark/base-benchmark-v1.jsonl
```

---

## 8. Definition of Done complessiva

Il progetto è concluso quando:

- [ ] esiste un benchmark congelato;
- [ ] il modello base è stato misurato;
- [ ] il dataset è validato e versionato;
- [ ] ogni esempio ha provenienza;
- [ ] l'ambiente è riproducibile;
- [ ] il training QLoRA completa senza errori;
- [ ] l'adapter è ricaricabile;
- [ ] l'adapter supera i gate definiti;
- [ ] non introduce raccomandazioni finanziarie;
- [ ] non inventa numeri oltre la soglia ammessa;
- [ ] produce JSON valido almeno nel 99% dei casi;
- [ ] model card, licenze e checksum sono disponibili;
- [ ] il contratto runtime è pronto per VIS.

---

## 9. Non obiettivi

Sono esclusi:

- pretraining di un modello da zero;
- full fine tuning di tutti i pesi;
- RLHF;
- trading automatico;
- consulenza finanziaria personalizzata;
- recupero autonomo di notizie;
- previsione del prezzo futuro;
- integrazione Java/Spring Boot;
- serving con Ollama o llama.cpp;
- interfaccia utente;
- modifica degli algoritmi deterministici VIS.

---

## 10. Rischi principali

### Dataset sintetico troppo uniforme

Mitigazione:

- più categorie;
- prompt teacher variati;
- revisione umana;
- casi manuali;
- deduplicazione semantica.

### Imitazione dello stile, non della logica

Mitigazione:

- input sintetici nuovi;
- test per famiglie non presenti nel training;
- metriche di evidenza;
- benchmark avversariali.

### Allucinazioni finanziarie

Mitigazione:

- simboli sintetici;
- evidence fields;
- validator numerico;
- output strutturato;
- penalizzazione/reiezione degli esempi non grounded.

### Overfitting

Mitigazione:

- dataset separati;
- poche epoche;
- validation;
- confronto base/few-shot/adapter;
- aumento graduale della capacità LoRA.

### Dipendenza da un teacher

Mitigazione:

- casi manuali;
- regole deterministiche;
- critic;
- provenienza;
- valutazione indipendente.

### Incompatibilità software

Mitigazione:

- lock file;
- smoke training;
- revision del modello;
- configurazioni versionate;
- ambiente container opzionale.

### Limiti hardware

Mitigazione:

- QLoRA 4 bit;
- batch 1;
- gradient accumulation;
- gradient checkpointing;
- sequenze contenute;
- training pilota.

---

## 11. Principi di governance

1. Ogni dataset release è immutabile.
2. Ogni training run punta a un commit Git.
3. Ogni adapter dichiara base model e revision.
4. Ogni esempio dichiara la provenienza.
5. Il test set non viene usato per il training.
6. Nessun output teacher entra direttamente nel dataset release.
7. Le metriche negative vengono conservate, non nascoste.
8. Un adapter non viene promosso solo perché la training loss è più bassa.
9. Le regole finanziarie critiche restano fuori dal modello.
10. Ogni output del modello deve poter essere rifiutato da VIS.

---

## 12. Primo incremento da implementare

Primo incremento autorizzato:

```text
TRAIN-00
TRAIN-01
TRAIN-02
```

Stato effettivo del primo incremento:

- [x] modello target iniziale scelto;
- [x] ADR, licenze e hardware formalmente documentati;
- [x] contratti JSON definiti;
- [ ] 10 esempi manuali (3 implementati);
- [x] validatore automatico prototipale in Node.js;
- [ ] validator TRAIN-02 completo con CLI, report e test automatici;
- [x] nessun training ancora eseguito.

Questo evita di investire tempo nella GPU prima di aver stabilizzato il problema e i criteri di qualità.
