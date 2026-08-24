# TRAIN-05 RunPod Runbook

Questo runbook prepara lo smoke teacher/critic, ma **non autorizza né crea risorse cloud**. Il tooling locale usa esclusivamente backend fake. Prima dello smoke occorrono una nuova conferma operativa, la revisione immutabile del modello/tokenizer e la tariffa visibile della GPU. Prima del bulk occorre una distinta approvazione economica esplicita.

## Gate prima del Pod

1. Rieseguire `vis-teacher validate-config` e risolvere entrambi i blocker di revisione senza usare `main` o revisioni mobili.
2. Riesaminare Gemma Terms, Prohibited Use Policy e model card ufficiale; aggiornare il license review se i termini sono cambiati.
3. Generare il piano con `vis-teacher smoke-plan`: deve contenere 20 scenari, tutte le 14 categorie e 40 candidate slot.
4. Registrare provider, Secure Cloud, GPU con almeno 80 GiB VRAM, tariffa oraria, volume e immagine/digest proposti.
5. Ottenere conferma prima di avviare qualsiasi risorsa fatturabile.

## Configurazione prevista

- RunPod Secure Cloud, singola GPU con almeno 80 GiB VRAM.
- Volume persistente dimensionato dopo aver verificato modello, cache e artifact.
- `HF_TOKEN` inserito come secret RunPod, mai in file, shell history, output o manifest.
- Processo del Pod mantenuto con `sleep infinity`; lavoro lungo dentro `tmux`.
- Checkout della revisione Git approvata e installazione del wheel locale.
- Teacher e critic sullo stesso checkpoint fissato, in passaggi separati; il critic non riscrive il candidato.

## Smoke e stop obbligatorio

Eseguire soltanto i 20 scenari del piano: due candidati per scenario e una critic review per ogni candidato parseable. Catturare revisione modello/tokenizer, immagine, GPU/VRAM, dipendenze, token, latenze, throughput, durata e tariffa. Fallimenti, output non JSON e rejection devono restare contabilizzati senza retry che sostituisca il primo output.

Dopo i 40 slot:

1. fermare la generazione;
2. produrre il report con costo misurato e stima per 1.000 candidati più critic;
3. trasferire artifact sanitizzati e checksum;
4. verificare localmente i checksum;
5. distruggere Pod e volume non necessari;
6. richiedere una nuova approvazione economica prima del bulk.

Nessun comando del pacchetto `vis_training.teacher` effettua provisioning RunPod. Il bulk da 500 scenari/1.000 candidati resta bloccato finché non viene autorizzato esplicitamente.

## Calibration v1 eseguita il 2026-08-23

L'utente ha autorizzato calibration e bulk con un tetto complessivo di 50 USD, imponendo uno stop e una review dei risultati dopo la calibration. Il sotto-limite operativo della calibration è 10 USD; il bulk non deve partire nella stessa esecuzione.

La calibration usa 50 scenari bilanciati sulle 14 categorie e 100 candidate slot. Generare il piano localmente:

```bash
PYTHONPATH=src .venv/bin/python -m vis_training.teacher.cli --root . calibration-plan \
  --scenarios datasets/candidates/scenarios-v1.jsonl \
  --output outputs/train-05/calibration-plan.json \
  --dataset-output outputs/train-05/calibration-scenarios.jsonl \
  --count 50 \
  --program-budget-cap-usd 50 \
  --calibration-budget-cap-usd 10
```

I comandi storici della v1 usavano run ID distinti dallo smoke:

```bash
PYTHONPATH=src python -m vis_training.teacher.cli --root . runpod-generate \
  --run-id train-05-calibration \
  --scenarios outputs/train-05/calibration-scenarios.jsonl \
  --output outputs/train-05/calibration-candidates.jsonl \
  --manifest outputs/train-05/calibration-manifest.json
PYTHONPATH=src python -m vis_training.teacher.cli --root . runpod-critic \
  --run-id train-05-calibration-critic-v2 \
  --scenarios outputs/train-05/calibration-scenarios.jsonl \
  --candidates outputs/train-05/calibration-candidates.jsonl \
  --output outputs/train-05/calibration-critics.jsonl
```

Fermare la run al raggiungimento di 10 USD, anche se incompleta. Dopo artifact transfer, checksum e cleanup, presentare metriche e costo all'utente; il bulk richiede una nuova decisione esplicita.

## Calibration v2 — preparazione locale

La review umana della v1 ha bloccato il bulk. La v2 usa `config/teacher-v2.json`, `teacher-prompt-v2` e `critic-prompt-v3`; deve usare percorsi e run ID nuovi per non sovrascrivere gli artifact v1:

```bash
PYTHONPATH=src .venv/bin/python -m vis_training.teacher.cli --root . \
  --config config/teacher-v2.json calibration-plan \
  --scenarios datasets/candidates/scenarios-v1.jsonl \
  --output outputs/train-05/calibration-v2-plan.json \
  --dataset-output outputs/train-05/calibration-v2-scenarios.jsonl \
  --count 50 \
  --program-budget-cap-usd 50 \
  --calibration-budget-cap-usd 10
```

L'esecuzione RunPod della v2 resta vietata finché test locali, soglie di go/no-go e autorizzazione operativa non sono confermati. Il bulk resta separato e bloccato.

## Verifica locale

```bash
cd vis-model-training
.venv/bin/vis-teacher --root . validate-config
.venv/bin/vis-teacher --root . smoke-plan \
  --scenarios datasets/candidates/scenarios-v1.jsonl \
  --output outputs/train-05/smoke-plan.json \
  --dataset-output outputs/train-05/smoke-scenarios.jsonl \
  --count 20
.venv/bin/python -m pytest tests/teacher
```

Sul Pod, dopo installazione e cattura ambiente:

```bash
vis-teacher --root . runpod-generate \
  --scenarios outputs/train-05/smoke-scenarios.jsonl \
  --output outputs/train-05/smoke-candidates.jsonl \
  --manifest outputs/train-05/smoke-manifest.json
vis-teacher --root . runpod-critic \
  --scenarios outputs/train-05/smoke-scenarios.jsonl \
  --candidates outputs/train-05/smoke-candidates.jsonl \
  --output outputs/train-05/smoke-critics.jsonl
```
