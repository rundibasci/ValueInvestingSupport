# TRAIN-03 — Validation

## Acceptance Criteria

- [x] Il benchmark contiene almeno 50 casi validi e rispetta la distribuzione minima delle nove categorie.
- [x] Benchmark, ground truth, rubrica, schemi, prompt e formule metriche sono congelati e hashati prima della run canonica.
- [x] Nessun caso benchmark è incluso nel seed TRAIN-01 o in dataset destinati al training.
- [x] Modello, tokenizer, immagine e dipendenze sono fissati a revision/versioni immutabili.
- [x] Il runner preserva il primo output, supporta resume senza sovrascrittura e registra manifest e risultato per ogni caso.
- [x] La run canonica usa RunPod Secure Cloud, NVIDIA L4 24 GB e BF16 oppure documenta un fallback preventivamente approvato.
- [x] Decoding canonico: `temperature=0.0`, `do_sample=false`, `max_new_tokens=1024`, batch size `1`.
- [x] Tutte le metriche automatiche della roadmap sono prodotte globalmente e per categoria con denominatori documentati.
- [x] Almeno 20 casi sono revisionati manualmente con la rubrica versionata e coprono tutte le categorie.
- [x] Gli errori principali sono classificati e almeno tre comportamenti da migliorare sono espliciti e misurabili.
- [x] Report e manifest sono sanitizzati, recuperati localmente e verificati prima della rimozione delle risorse RunPod.
- [x] Pod, volume e risorse fatturabili non necessarie sono rimossi dopo l'esecuzione e il costo consuntivo è registrato.
- [x] Nessun token, segreto, peso, cache modello o dato di pagamento è commesso o incluso nei report.
- [x] Runtime, API, database e frontend VIS restano invariati.

## Benchmark Test Matrix

| Scenario | Expected result |
|---|---|
| Sottovalutazione robusta | Almeno 7 casi con label e ground truth complete |
| Value trap | Almeno 7 casi che separano valutazione e qualità |
| Sopravvalutazione | Almeno 6 casi |
| Fair value | Almeno 5 casi |
| Dividendo a rischio | Almeno 5 casi con evidenze pertinenti |
| Dati insufficienti | Almeno 5 casi con classificazione/review coerenti |
| Dati obsoleti | Almeno 5 casi con revisione umana richiesta |
| Contraddizioni | Almeno 5 casi con revisione umana richiesta |
| Casi avversariali | Almeno 5 casi senza esecuzione o propagazione di istruzioni |
| Record fuori schema | Rifiutato dalla CLI TRAIN-02 prima del freeze |
| ID duplicato | Rifiutato prima della run |
| Caso presente nel training | Gate di contaminazione fallisce |

## Runner and Metrics Test Matrix

| Scenario | Expected result |
|---|---|
| Output JSON e schema validi | Parsing riuscito e metriche aggiornate una volta |
| Output non JSON | Primo output preservato, parse failure registrato, run continua |
| Raccomandazione proibita | Violazione conteggiata senza alterare l'output |
| Numero non supportato | Violazione collegata all'ID del caso |
| Interruzione a metà run | Resume elabora soltanto i casi mancanti |
| Risultato già presente | Non sovrascritto né duplicato |
| OOM nello smoke test | Run canonica bloccata; tuning o fallback richiede approvazione |
| Hash benchmark diverso | Run canonica rifiutata |
| Token assente/non autorizzato | Errore sanitizzato, nessun token nei log |
| Report globale/per categoria | Conteggi e denominatori coerenti con i risultati per caso |

## Regression Checks

- [x] I 31 test TRAIN-02 continuano a passare (46 test totali passano).
- [x] La CLI Python valida il seed TRAIN-01 e il benchmark TRAIN-03.
- [x] Il validatore Node continua a passare sui dieci casi TRAIN-01.
- [x] Schemi e prompt esistenti non cambiano salvo decisione esplicita e versionata.
- [x] Nessuna dipendenza TRAIN entra nei moduli backend/frontend VIS.
- [x] `.venv`, cache Hugging Face, pesi, checkpoint, output temporanei e secrets non sono tracciati.
- [x] I report non contengono token, percorsi host sensibili, payload non sanitizzati o dati di pagamento.
- [x] `git diff --check` passa e la working tree finale contiene soltanto artefatti intenzionali.

## Verification Commands

I comandi definitivi saranno implementati e sostituiranno i placeholder dopo l'approvazione della spec. Non devono creare risorse RunPod automaticamente.

```bash
# Local validation
cd vis-model-training
.venv/bin/python -m pytest
.venv/bin/python -m scripts.validate_dataset \
  --dataset datasets/seed-dataset-v1.jsonl \
  --input-schema schemas/thesis-input.schema.json \
  --output-schema schemas/thesis-output.schema.json
.venv/bin/python -m scripts.validate_dataset \
  --dataset datasets/benchmark/base-benchmark-v1.jsonl \
  --input-schema schemas/thesis-input.schema.json \
  --output-schema schemas/thesis-output.schema.json
node scripts/validate-dataset.mjs

# TRAIN-03 tooling; exact module names are established during implementation
.venv/bin/python -m pytest tests/benchmark
.venv/bin/python -m vis_training.benchmark.verify_freeze
.venv/bin/python -m vis_training.benchmark.compute_metrics

cd ..
git diff --check
git status --short
```

## Manual Validation

1. Confermare nella console RunPod che il Pod appartenga a Secure Cloud e monti una singola L4 24 GB.
2. Verificare nel container GPU, driver, CUDA, BF16, RAM, disco, digest immagine e revisioni modello/tokenizer.
3. Confermare che `HF_TOKEN` sia configurato come secret e non compaia in ambiente stampato, history, log o manifest.
4. Eseguire lo smoke test non canonico e verificare VRAM massima, latenza, spazio e recupero degli artefatti.
5. Prima della run canonica confrontare gli hash con il freeze manifest e confermare che nessun risultato sia già stato letto per modificare ground truth o rubriche.
6. Ispezionare completezza e unicità dei risultati dei 50+ casi e confrontare un campione con output grezzo e parsato.
7. Revisionare almeno 20 casi con la rubrica, includendo tutte le categorie e i principali failure mode.
8. Verificare report finale, tassonomia errori e almeno tre comportamenti misurabili da migliorare.
9. Scaricare gli artefatti, verificare checksum localmente e poi eliminare Pod, volume e risorse fatturabili non necessarie.

## Merge Gate

TRAIN-03 è merge-ready soltanto quando il benchmark di almeno 50 casi è congelato, separato dal training e validato; la baseline BF16 di `google/gemma-3-4b-it` è stata eseguita su RunPod Secure Cloud con ambiente e revisioni riproducibili; risultati, metriche automatiche e review manuale di almeno 20 casi sono completi; gli errori principali e almeno tre obiettivi di miglioramento sono documentati; report e manifest sono sanitizzati e recuperati; le risorse RunPod non necessarie sono state rimosse; regressioni TRAIN passano e il runtime VIS resta invariato. Nessuna fase di training può iniziare prima del superamento di questo gate.
