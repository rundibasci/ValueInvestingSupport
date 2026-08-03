# TRAIN-01 — Implementation Plan

## 1. Allineare i contratti della fase

1. Trattare i tre esempi, gli schemi e il prompt esistenti come baseline esplorativa da preservare.
2. Aggiornare README e documentazione TRAIN-01 da tre a dieci casi, eliminando i gate ormai superati che richiedono esattamente tre record.
3. Confermare che il completamento resta contract-only e non include runtime VIS, dipendenze AI, modello, GPU o deliverable TRAIN-02.

## 2. Definire i sette scenari mancanti

1. Assegnare `VIS4`–`VIS10` e identificatori univoci ai casi: impresa solida ma sopravvalutata, fair value, dividendo elevato ma insostenibile, leva elevata, FCF negativo/in deterioramento, indicatori contraddittori e dati obsoleti.
2. Definire per ciascun caso segnali di input, classificazione attesa, livello di confidence, warning deterministici e requisito di revisione umana.
3. Verificare che la matrice complessiva copra tutte le cinque classificazioni ammesse e distingua attrattività del prezzo, qualità dell'impresa e qualità dei dati.

## 3. Creare gli esempi manuali

1. Aggiungere `example-004.json`–`example-010.json` conservando il formato conversazionale e i metadati dei casi esistenti.
2. Usare esclusivamente società e simboli sintetici, senza conoscenza esterna o eventi aziendali reali.
3. Collegare ogni claim di `bullCase` e `bearCase` a campi non nulli presenti nell'input e mantenere ogni numero dell'output riconducibile all'input.
4. Impostare warning, `dataWarnings` e `humanReviewRequired` coerentemente nei casi rischiosi, contraddittori o obsoleti.

## 4. Ricostruire il seed dataset

1. Aggiornare `datasets/seed-dataset-v1.jsonl` con i dieci documenti sorgente, uno per riga e in ordine numerico.
2. Non racchiudere i record in un array e preservare la serializzazione JSON dei messaggi incorporati.
3. Verificare corrispondenza byte-logica fra ogni file esempio e la relativa riga JSONL, inclusi metadati e ordine dei messaggi.

## 5. Adeguare i controlli TRAIN-01

1. Estendere `scripts/validate-dataset.mjs` al conteggio di dieci record e alla tassonomia completa, senza introdurre la CLI Python o la suite di TRAIN-02.
2. Validare sintassi, schemi Draft 2020-12, unicità degli identificatori, `evidenceFields`, numeri supportati dall'input, classificazioni e revisione umana.
3. Aggiungere controlli espliciti contro raccomandazioni `BUY`/`SELL`/`HOLD`, Markdown e testo fuori dal JSON.
4. Eseguire un controllo negativo con una copia temporanea corrotta o una fixture effimera, senza commettere dataset di test appartenenti a TRAIN-02.

## 6. Documentazione e merge readiness

1. Aggiornare `vis-model-training/README.md` marcando TRAIN-01 completa soltanto dopo il superamento di tutti i gate; lasciare TRAIN-02 prototipale/incompleta.
2. Eseguire tutti i comandi di validazione, `git diff --check` e il controllo di igiene del repository.
3. Confermare che backend, frontend, Compose, dipendenze applicative e configurazione runtime VIS non siano cambiati.
4. Riesaminare manualmente i dieci casi e registrare l'esito nella checklist di validazione prima del merge.
