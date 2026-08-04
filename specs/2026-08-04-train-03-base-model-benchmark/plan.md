# TRAIN-03 — Implementation Plan

## 1. Contratti, lock e sicurezza

1. Definire layout degli artefatti TRAIN-03 per benchmark, runner, metriche, rubriche, manifest e report.
2. Selezionare e registrare revision immutabili di `google/gemma-3-4b-it` e tokenizer dopo la verifica dei termini di accesso.
3. Bloccare immagine Docker per digest e dipendenze Python/CUDA compatibili con L4 in un lock isolato sotto `vis-model-training/`.
4. Definire schema versionato del run manifest e dei risultati per caso, includendo hash, versioni, hardware, parametri, tempi ed errori sanitizzati.
5. Aggiornare `.gitignore` e controlli di secret hygiene per cache Hugging Face, token, output temporanei, pesi e file RunPod locali.

## 2. Benchmark e ground truth

1. Definire template dei casi, tassonomia, label attese, requisiti di human review e regole di separazione dal training.
2. Scrivere almeno 50 casi sintetici secondo la distribuzione minima della roadmap, con ID e scenari distinti.
3. Validare ogni record con la CLI TRAIN-02 e aggiungere test sui conteggi per categoria, unicità e assenza di contaminazione dal seed/training.
4. Definire deterministicamente il campione di almeno 20 casi per revisione manuale, coprendo tutte le categorie.
5. Congelare benchmark, schemi, prompt, rubrica e formule con versione e hash SHA-256 prima della run canonica.

## 3. Runner di inferenza

1. Implementare caricamento text-only BF16 del modello e tokenizer alle revision fissate, con evaluation mode e device CUDA esplicito.
2. Applicare prompt e chat template versionati con decoding greedy, `temperature=0.0`, `do_sample=false`, `max_new_tokens=1024` e batch size `1`.
3. Implementare esecuzione incrementale e idempotente che preserva il primo output e non sovrascrive risultati esistenti.
4. Registrare output grezzo, parsing, token, latenza ed errori per caso senza stampare token o payload completi nei log operativi.
5. Aggiungere test unitari con modello finto per prompt construction, resume, duplicati, parsing, manifest e failure handling, senza rete o GPU.

## 4. Metriche e revisione umana

1. Formalizzare denominatori, arrotondamenti e trattamento degli output non validi per tutte le metriche della roadmap.
2. Implementare metriche globali e per categoria riusando schema e validator TRAIN-02 senza mutare gli output.
3. Creare rubrica e formato di review per correttezza, equilibrio, rischi, grounding e utilità.
4. Implementare aggregazione delle review di almeno 20 casi e collegamento agli ID del benchmark.
5. Generare una tassonomia degli errori e un report che richieda almeno tre comportamenti misurabili da migliorare.

## 5. RunPod runbook e smoke test

1. Documentare creazione manuale di un Pod Secure Cloud L4 24 GB con regione, volume, immagine digestata, processo persistente `sleep infinity`, porte minime e regole di accesso.
2. Documentare inserimento di `HF_TOKEN` tramite RunPod Secrets, senza valori di esempio realistici o comandi che lo scrivano nella shell history.
3. Preparare comandi per checkout del commit approvato, installazione/verifica, download checkpoint e stampa del manifest ambientale.
4. Eseguire una prova infrastrutturale e uno smoke test su pochi casi non canonici, verificando BF16, VRAM, spazio disco, latenza e recupero artefatti.
5. Registrare eventuali adattamenti non semantici; richiedere approvazione prima di cambiare GPU, precisione, modello, prompt o configurazione canonica.

## 6. Benchmark canonico e report

1. Verificare hash e working tree, quindi eseguire una sola run canonica completa sul benchmark congelato.
2. Controllare completezza degli output, assenza di duplicati, manifest e corrispondenza fra ID attesi ed eseguiti.
3. Calcolare metriche automatiche, completare la revisione manuale e classificare gli errori principali.
4. Produrre il report sotto `reports/baseline/gemma-3-4b-it-v1/` con almeno tre comportamenti da migliorare.
5. Aggiornare `vis-model-training/README.md` con risultati, limiti, costi e stato TRAIN-03 soltanto dopo il superamento del gate.

## 7. Cleanup e merge readiness

1. Esportare report e manifest, verificarne checksum e leggibilità dalla macchina locale.
2. Arrestare ed eliminare Pod, volume e risorse fatturabili non necessarie, registrando il costo consuntivo senza dati di pagamento.
3. Eseguire suite TRAIN, validazione benchmark, test runner/metriche, secret scan mirato e `git diff --check`.
4. Confermare che benchmark e output non siano inclusi nei futuri dataset di training e che VIS runtime resti invariato.
5. Marcare TRAIN-03 completa soltanto quando tutti i criteri e il merge gate risultano soddisfatti.
