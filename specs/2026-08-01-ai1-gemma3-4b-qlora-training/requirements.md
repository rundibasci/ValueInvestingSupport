# TRAIN-01 — Definizione del task e del formato dataset

## Context

TRAIN è un percorso di sviluppo parallelo a VIS. VIS resta il motore deterministico e autorevole per raccolta dati, DCF, valore intrinseco, margin of safety, Value Score e warning. Il modello linguistico interpreta esclusivamente il contesto già calcolato da VIS e produce una tesi strutturata, verificabile e sottoposta a revisione umana.

Il target futuro è `google/gemma-3-4b-it`, adattato tramite Supervised Fine-Tuning con QLoRA e adapter PEFT mantenendo congelato il modello base. TRAIN-00 ha successivamente classificato i primi tre esempi e il validatore Node come lavoro esplorativo: TRAIN-01 si completa portando il catalogo manuale a dieci casi, senza anticipare la CLI Python e la suite di test di TRAIN-02.

## Scope

### Task del modello

Gemma deve:

- interpretare i risultati ricevuti da VIS;
- identificare aspetti positivi e negativi;
- evidenziare rischi e contraddizioni;
- collegare ogni affermazione bull/bear ai campi dell'input;
- individuare le condizioni che invalidano la tesi;
- produrre esclusivamente JSON conforme allo schema;
- richiedere revisione umana quando i dati sono dubbi, parziali, obsoleti, incoerenti o insufficienti.

Gemma non deve:

- calcolare DCF, valore intrinseco, margin of safety o Value Score;
- recuperare dati dal web o introdurre informazioni esterne;
- inventare dati, eventi, rischi o caratteristiche aziendali mancanti;
- sostituire i controlli deterministici di VIS;
- formulare istruzioni o raccomandazioni `BUY`, `SELL` o `HOLD`.

### Classificazioni ammesse

L'output usa esclusivamente:

- `POTENTIALLY_UNDERVALUED`
- `FAIRLY_VALUED`
- `POTENTIALLY_OVERVALUED`
- `UNDER_REVIEW`
- `INSUFFICIENT_DATA`

Queste classificazioni descrivono l'evidenza e non costituiscono raccomandazioni di investimento.

### Artefatti richiesti

```text
vis-model-training/
├── README.md
├── schemas/
│   ├── thesis-input.schema.json
│   └── thesis-output.schema.json
├── prompts/
│   └── system-prompt-v1.txt
├── examples/
│   ├── example-001.json
│   ├── example-002.json
│   ├── example-003.json
│   ├── example-004.json
│   ├── example-005.json
│   ├── example-006.json
│   ├── example-007.json
│   ├── example-008.json
│   ├── example-009.json
│   └── example-010.json
└── datasets/
    └── seed-dataset-v1.jsonl
```

### Contratto di input

`thesis-input.schema.json` usa JSON Schema Draft 2020-12, rifiuta proprietà aggiuntive e richiede:

- `symbol`, `analysisDate`, `marketPrice`;
- `intrinsicValue`, `marginOfSafetyPercent`, `valueScore`, anche null quando indisponibili;
- `revenueTrend`, `earningsTrend`, `freeCashFlowTrend`;
- `dataQuality` e `deterministicWarnings`.

Può inoltre contenere `companyName`, `dividendYieldPercent`, `payoutRatioPercent` e `netDebtToEbitda`. Trend, qualità dati, intervalli numerici e nullability seguono esattamente il contratto fornito nel piano TRAIN-01.

### Contratto di output

`thesis-output.schema.json` usa JSON Schema Draft 2020-12, rifiuta proprietà aggiuntive e richiede:

- `classification` e `confidence` compresa tra 0 e 1;
- `summary`, `bullCase`, `bearCase`;
- `keyRisks`, `keyAssumptions`, `invalidationConditions`, `dataWarnings`;
- `humanReviewRequired`.

Ogni elemento di `bullCase` e `bearCase` contiene `claim` ed `evidenceFields`. `evidenceFields` può indicare soltanto i campi finanziari esplicitamente ammessi dallo schema fornito.

### Prompt e formato dataset

- `system-prompt-v1.txt` contiene le dodici regole del piano TRAIN-01, incluse grounding, non-ricalcolo, separazione fra valutazione e qualità, revisione umana e solo-JSON.
- Ogni esempio è un documento conversazionale con messaggi `system`, `user`, `assistant` e blocco `metadata`.
- `user.content` e `assistant.content` sono stringhe contenenti JSON serializzato.
- `seed-dataset-v1.jsonl` contiene esattamente i dieci esempi, uno per riga, senza array esterno e nello stesso ordine dei file sorgente.
- Gli esempi usano simboli e aziende sintetici per ridurre il richiamo di conoscenza pregressa.
- I tre casi esistenti restano invariati salvo correzioni documentate: impresa solida e sottovalutata, potenziale value trap e dati insufficienti.
- I sette casi aggiuntivi coprono: impresa solida ma sopravvalutata, fair value, dividendo elevato ma insostenibile, leva finanziaria elevata, FCF negativo o in deterioramento, indicatori contraddittori e dati obsoleti.
- Ogni scenario deve essere distinguibile tramite `metadata.scenarioType`, usare un identificatore e un simbolo sintetico univoci e contenere soltanto evidenze presenti nell'input.

## Decisions

1. **TRAIN è parallelo a VIS.** Gli artefatti vivono in `vis-model-training/`, separati dal backend Java e dal normale ciclo di avvio dell'applicazione.
2. **TRAIN-01 è contract-only.** Non scarica modelli, non installa dipendenze AI e non richiede GPU.
3. **VIS conserva l'autorità numerica.** Il modello interpreta valori e warning forniti, senza ricalcolarli.
4. **Dataset conversazionale.** Il formato `messages` è compatibile con il chat template di Gemma e con TRL.
5. **Esempi sintetici.** I simboli `VIS1`–`VIS10` evitano contaminazione da conoscenze su società reali.
6. **Output chiuso e auditabile.** JSON Schema, enumerazioni e riferimenti di evidenza limitano output liberi e affermazioni non tracciabili.
7. **Boundary decision-support.** Non sono ammesse istruzioni operative. Il README chiarisce che gli output richiedono revisione umana e non sono consulenza finanziaria.

## Out of Scope

- Download di Gemma o accesso a Hugging Face.
- Installazione di PyTorch, Transformers, TRL, PEFT, bitsandbytes o altre librerie AI.
- Dockerfile o `docker-compose.training.yml`.
- Scelta dei parametri LoRA o configurazione `BitsAndBytesConfig`.
- Ambiente Conda, CUDA, GPU, `SFTTrainer`, fine-tuning o salvataggio adapter.
- Generazione massiva tramite frontier model.
- Implementazione della CLI Python, dei codici errore stabili, dei report CI e della suite di almeno 15 test previsti da TRAIN-02.
- Benchmark quantitativo, conversione modello, inferenza o integrazione Ollama/VIS.

Questi elementi appartengono a TRAIN-02 e alle fasi successive. Il validatore Node esistente può essere esteso soltanto quanto necessario a verificare i dieci record e i gate di TRAIN-01; non deve trasformarsi nell'implementazione anticipata di TRAIN-02.

## Compatibility and Risks

- TRAIN-01 non modifica runtime, API, database, frontend o Compose di VIS.
- Lo schema iniziale è volutamente piccolo; l'evoluzione deve essere versionata per non rendere ambigui prompt e dataset già prodotti.
- Esempi manuali errati possono insegnare correlazioni scorrette. Ogni claim deve quindi essere verificato contro i campi citati.
- I sette nuovi casi devono variare segnali, classificazioni e condizioni di revisione senza introdurre nuove proprietà fuori schema o duplicare superficialmente i tre casi esistenti.
- I casi intenzionalmente contraddittori, obsoleti o rischiosi devono esporre warning coerenti e richiedere revisione umana; nessun dato mancante o problematico può essere sostituito con uno zero inventato.
- `format: date` richiede una validazione JSON Schema che abiliti esplicitamente il format checker.
- La classificazione non elimina il rischio che un testo venga interpretato come consiglio: prompt, README e future superfici VIS devono mantenere il confine di decision support e il disclaimer MiFID II.
