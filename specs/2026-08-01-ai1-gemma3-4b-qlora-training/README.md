# TRAIN — Investment Thesis Agent

## Percorso parallelo a VIS

TRAIN è il percorso dedicato alla costruzione dell'Investment Thesis Agent basato su Gemma 3 4B. Procede in parallelo allo sviluppo applicativo di ValueInvestingSupport senza trasferire al modello le responsabilità deterministiche della piattaforma.

```text
VIS                                         TRAIN
────────────────────────────────────        ───────────────────────────────
dati finanziari e qualità                   contratto input/output
DCF e valore intrinseco              ───►   interpretazione delle evidenze
margin of safety e Value Score              tesi bull/bear auditabile
warning deterministici                      rischi e condizioni invalidanti
```

VIS resta l'autorità sui dati e sui calcoli. Gemma non recupera dati, non ricalcola metriche e non emette ordini di investimento. La sua funzione futura è trasformare l'evidenza strutturata in una tesi concisa e verificabile, con revisione umana quando necessaria.

## Sequenza aggiornata delle fasi

1. **TRAIN-00 — Decisioni e prerequisiti:** ADR, governance, licenze e verifica hardware; completo con GO condizionato.
2. **TRAIN-01 — Task e formato dataset (questa spec):** schemi, system prompt, dieci esempi sintetici e seed dataset JSONL; completo.
3. **TRAIN-02 — Validator del dataset:** package e CLI Python, validazioni strutturali/semantiche, codici errore, report e suite automatica di almeno 15 test.
4. **TRAIN-03 — Benchmark del modello base:** baseline riproducibile di Gemma prima di qualsiasi fine-tuning.

La roadmap autorevole completa vive in `vis-model-training/README.md`. I documenti di questa directory definiscono TRAIN-01, ora completata; TRAIN-02 richiede una specifica autonoma prima dell'implementazione.

## Documenti

- `requirements.md`: comportamento ammesso, contratti, decisioni ed esclusioni.
- `plan.md`: ordine di creazione degli artefatti TRAIN-01.
- `validation.md`: criteri di accettazione, test matrix e merge gate.

## Output previsto da TRAIN-01

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

## Decision-support boundary

Le classificazioni prodotte dal modello descrivono l'evidenza disponibile e non equivalgono a raccomandazioni. Tutti gli output futuri dovranno essere sottoposti a controllo umano e accompagnati dal disclaimer VIS: “This is a decision-support tool, not investment advice (MiFID II).”
