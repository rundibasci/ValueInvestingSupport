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

## Verifica locale

```bash
cd vis-model-training
.venv/bin/vis-teacher --root . validate-config
.venv/bin/vis-teacher --root . smoke-plan \
  --scenarios datasets/candidates/scenarios-v1.jsonl \
  --output outputs/train-05/smoke-plan.json \
  --count 20
.venv/bin/python -m pytest tests/teacher
```
