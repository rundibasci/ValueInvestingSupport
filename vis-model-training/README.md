# VIS Model Training — TRAIN-01

Questa directory contiene il contratto iniziale per il futuro Investment Thesis Agent di ValueInvestingSupport. È un percorso parallelo al runtime VIS: non contiene codice Java, dipendenze AI, modello, fine-tuning o integrazione di inferenza.

## Responsabilità

VIS rimane l'unica autorità per:

- dati finanziari e qualità dei dati;
- DCF e valore intrinseco;
- margin of safety e Value Score;
- warning e controlli deterministici.

Il futuro modello `google/gemma-3-4b-it` dovrà esclusivamente interpretare questi valori e produrre una tesi strutturata e auditabile. Non dovrà recuperare informazioni esterne, ricalcolare metriche, inventare dati o formulare istruzioni buy/sell/hold.

Le classificazioni descrivono l'evidenza disponibile e non sono raccomandazioni di investimento. Gli output richiedono supervisione umana e restano soggetti al disclaimer VIS: “This is a decision-support tool, not investment advice (MiFID II).”

## Contenuto TRAIN-01

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

- `schemas/` definisce i contratti JSON Schema Draft 2020-12.
- `prompts/` contiene il prompt di sistema versionato.
- `examples/` contiene tre conversazioni manuali basate su aziende sintetiche.
- `datasets/` contiene gli stessi esempi in formato JSONL conversazionale compatibile con il futuro flusso TRL.

I campi `content` dei messaggi `user` e `assistant` sono stringhe contenenti JSON serializzato. Il dataset JSONL contiene un documento autonomo per riga e non è racchiuso in un array.

## Validazione

TRAIN-01 non richiede GPU o librerie AI. I controlli sintattici di base possono essere eseguiti con `jq`:

```bash
jq empty vis-model-training/schemas/*.json
jq empty vis-model-training/examples/*.json
test "$(grep -cve '^[[:space:]]*$' vis-model-training/datasets/seed-dataset-v1.jsonl)" -eq 3
jq -c . vis-model-training/datasets/seed-dataset-v1.jsonl >/dev/null
jq -r '.messages[] | select(.role == "user" or .role == "assistant") | .content' \
  vis-model-training/examples/*.json | jq empty
```

Il validatore TRAIN-01 usa soltanto moduli integrati di Node.js e controlla contratti, JSON incorporato, riferimenti di evidenza, scenari e corrispondenza esatta tra esempi e JSONL:

```bash
node vis-model-training/scripts/validate-dataset.mjs
```

Per ogni esempio resta necessaria anche la revisione umana della coerenza finanziaria e dell'assenza di informazioni esterne.

## Fasi successive

Docker Compose dedicato, stack QLoRA, GPU NVIDIA, parametri LoRA, `SFTTrainer`, adapter PEFT e inferenza sono intenzionalmente esclusi da TRAIN-01 e saranno specificati separatamente a partire da TRAIN-02.
