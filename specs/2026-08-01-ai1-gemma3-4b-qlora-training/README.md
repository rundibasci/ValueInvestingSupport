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

## Sequenza delle fasi

1. **TRAIN-01 — Task e formato dataset (questa spec):** schemi, system prompt, tre esempi sintetici e seed dataset JSONL. Nessun modello, libreria AI o GPU.
2. **TRAIN-02 — Infrastruttura QLoRA:** `docker-compose.training.yml` dedicato, ambiente GPU NVIDIA, dipendenze pinning e smoke test per `google/gemma-3-4b-it`.
3. **TRAIN-03 — Dataset expansion e quality gate:** ampliamento controllato, validazione automatica, deduplicazione e split train/validation/test.
4. **TRAIN-04 — Fine-tuning e valutazione:** SFT QLoRA, adapter PEFT, metriche strutturali e revisione qualitativa esperta.
5. **TRAIN-05 — Inferenza e integrazione VIS:** servizio separato, contratti API, osservabilità e fallback sicuro. Richiederà una specifica autonoma.

Solo TRAIN-01 è approvabile e implementabile attraverso i documenti di questa directory. Le fasi successive indicano la direzione ma non autorizzano ancora codice o infrastruttura.

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
│   └── example-003.json
└── datasets/
    └── seed-dataset-v1.jsonl
```

## Decision-support boundary

Le classificazioni prodotte dal modello descrivono l'evidenza disponibile e non equivalgono a raccomandazioni. Tutti gli output futuri dovranno essere sottoposti a controllo umano e accompagnati dal disclaimer VIS: “This is a decision-support tool, not investment advice (MiFID II).”
