# TRAIN-05 — Implementation Plan

## 1. Governance, contratti e configurazione

1. Registrare la verifica 2026-08-06 di Gemma Terms, Prohibited Use Policy e model card 27B con `licenseReviewId` stabile.
2. Risolvere la revisione immutabile del teacher soltanto immediatamente prima dello smoke e congelarla nel manifest.
3. Definire configurazione versionata di modello, precisione, decoding, candidate count, critic e ambiente RunPod.
4. Definire schemi JSON per candidate record, critic review, run manifest, progress state, generation report e review umana.
5. Definire retention, percorsi raw/ignored, artifact sanitizzati e strategia checksum/trasferimento.

## 2. Prompt teacher e critic

1. Creare `teacher-prompt-v1.txt` con contratto completo, regole e output JSON stretto.
2. Creare `critic-prompt-v1.txt` con rubriche, error codes e divieto di riscrittura.
3. Implementare composizione deterministica dei messaggi da scenario, prompt e schema.
4. Hashare prompt, schema e configurazione nel freeze manifest prima della prima run.
5. Aggiungere fixture golden per overvaluation, value trap, dividend risk, stale, insufficient e adversarial.

## 3. Pipeline candidate locale

1. Definire backend interface e fake backend deterministico per test offline.
2. Implementare generazione di due candidate index per scenario con seed derivati stabili.
3. Conservare primo output, token, latenza, parse/generation error e provenance completa.
4. Implementare resume idempotente, manifest compatibility e duplicate detection.
5. Implementare scrittura append-safe/atomica e stato esplicito per ogni tentativo.

## 4. Validazione e critic

1. Riutilizzare parser, schema e validatori TRAIN-02/03 senza indebolire le metriche strette.
2. Implementare validator finanziari mirati alle failure TRAIN-03.
3. Selezionare per il critic soltanto candidati parseable, preservando gli altri come rejection.
4. Implementare critic backend/pass separato e collegare review immutabili ai candidate ID.
5. Produrre verdict, punteggi, error codes e motivazioni senza generare una risposta corretta sostitutiva.

## 5. CLI, metriche e review umana

1. Aggiungere CLI per generate, resume, validate, critic, report, prepare-review e check-review.
2. Produrre metriche globali/per categoria su tentativi, parsing, schema, semantica, critic, token, latenza e costo.
3. Registrare costo orario, durata misurata, token input/output e costo totale/medio quando applicabili.
4. Preparare campione deterministico minimo di 30 candidate con copertura categorie e failure mode.
5. Rifiutare report incompleti, denominatori incoerenti o provenance mancante.

## 6. Ambiente e runbook RunPod

1. Definire immagine/digest e lock dependencies per Gemma 3 27B BF16.
2. Documentare Pod Secure Cloud, GPU 80 GB, volume, secret `HF_TOKEN`, `sleep infinity`, checkout revision e installazione.
3. Aggiungere cattura ambiente sanitizzata, VRAM, spazio, versioni e manifest.
4. Documentare smoke, resume, download/checksum e rimozione Pod/volume.
5. Impedire alla CLI locale di creare automaticamente risorse a pagamento.

## 7. Smoke RunPod e decisione bulk

1. Selezionare deterministicamente 20 scenari stratificati sulle 14 categorie.
2. Eseguire 40 candidati e critic su ogni candidato parseable.
3. Verificare JSON, schema, semantica, provenance, resume, VRAM, latenza, token e costo.
4. Recuperare artifact, verificarne checksum e rimuovere le risorse non necessarie.
5. Produrre un report smoke con stima documentata per 1.000 candidati più critic.
6. Fermarsi e richiedere approvazione esplicita prima del bulk canonico.

## 8. Bulk canonico e merge readiness

1. Dopo approvazione economica, generare 1.000 candidati per 500 scenari e review critic per ogni candidato parseable.
2. Riprendere run interrotte senza sovrascrivere o duplicare output.
3. Completare review umana di almeno 30 casi e registrare decisioni.
4. Produrre candidate, critic review e report finali con checksum e provenienza completa.
5. Rieseguire suite TRAIN-01–04, freeze/checksum, secret scan e controlli di non contaminazione.
6. Aggiornare README e validation; marcare TRAIN-05 completa soltanto dopo tutti i gate e senza promuovere dati in TRAIN-06.
