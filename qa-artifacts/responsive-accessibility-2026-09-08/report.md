# Audit responsive e accessibilità — 8 settembre 2026

## Valutazione

Base responsive discreta, usabilità smartphone debole nei flussi di lavoro complessi e accessibilità incompleta. Non è una certificazione WCAG né un test end-to-end del prodotto live.

Audit statico di un agente frontend dedicato, integrato da Chromium/Playwright su viewport 320, 390, 768 e 1440 px, altezza 844 px. Pagine misurate: login, screener, checklist, portfolio e watchlist (20 combinazioni). API intercettate: sessione INVESTOR fittizia, una riga screener sintetica, liste vuote e risposte 503 per API non simulate. Nessun backend reale o staging coinvolto. Portfolio e watchlist coperti solo negli stati iniziali/vuoti/errore, non con dati popolati. Nessun test su dispositivo fisico, Safari, screen reader o zoom testo. Script e misure JSON allegati; nessuna modifica al codice del prodotto.

## Risultati misurati

- Nessuno scorrimento orizzontale dell'intera pagina nelle 20 combinazioni. Questo non esclude contenuti compressi o problemi negli stati non testati.
- A 390 px, il campo email inizia a y=1042 px (schermata alta 844 px), con avviso sessione scaduta presente. Marketing e spaziatura precedono il login.
- A 390 px, la tabella screener inizia a y=2433 px e misura 1985 px dentro un contenitore da 308 px. A 320 px, il contenitore è 238 px. Le coordinate dipendono dai dati e dagli stati API simulati.
- La navigazione principale a 390 px contiene 707 px di contenuto in 350 px visibili; voci successive richiedono scorrimento laterale.
- Checklist: quattro controlli privi di label associata/aria-label (tipo, metrica, operatore, soglia), a tutte le larghezze.
- Bug tastiera confermato: focus sul pulsante Review e Invio navigano a `/securities/TEST` invece di `/securities/TEST/review` (vedi `reviewEnterDestination` nel JSON).
- A 768 px, la griglia criteri Checklist comprime il primo campo a circa 26 px: il layout rientra nella pagina ma il campo è praticamente illeggibile. Screenshot allegato.

## Problemi e priorità

| Priorità | Problema | Evidenza nel codice | Intervento |
|---|---|---|---|
| Alta | Ricerca molto faticosa su telefono: filtri sempre aperti e tabella amplissima | `frontend/src/pages/ScreenerPage.tsx:285`, `:363`, `:398` | Filtri collassabili, righe/card mobile con metriche essenziali, azioni vicine al titolo |
| Alta | Review eredita Enter/Spazio dalla riga, che naviga al dettaglio | `frontend/src/pages/ScreenerPage.tsx:226`, `:369`, `:398` | Link nativi e separazione degli eventi di riga dai controlli figli |
| Alta | Campi Checklist senza nome accessibile | `frontend/src/pages/ChecklistPage.tsx:98`, `:102`, `:105`, `:108` | Etichette visibili associate ai singoli campi |
| Alta | Testi slate-500 su fondi scuri sotto contrasto 4,5:1 | `frontend/src/pages/LoginPage.tsx:176`, `SecurityDetailPage.tsx:44`, `PortfolioPage.tsx:313` | Correggere token colore e misurare anche fondi con trasparenza |
| Media | Login troppo in basso su telefono | `frontend/src/pages/LoginPage.tsx:77` | Form prima dell'introduzione o introduzione compatta |
| Media | Menu mobile poco scopribile | `frontend/src/components/AppShell.tsx:36`, `:54` | Navigazione compatta con tutte le destinazioni raggiungibili chiaramente |
| Media | Campo criterio compresso a larghezze intermedie | `frontend/src/pages/ChecklistPage.tsx:96` | Griglia intermedia, più righe e breakpoint superiore per le sei colonne |
| Media | Doppio padding riduce spazio utile | `frontend/src/components/AppShell.tsx:79`, `ScreenerPage.tsx:242`, `SecurityDetailPage.tsx:122` | Un solo contenitore con padding responsive |
| Media | Tab ARIA incompleti | `frontend/src/pages/SecurityDetailPage.tsx:122` | Associazioni tab/panel e gestione frecce/focus coerente |
| Media | Grafici stretti e dati storici senza equivalente tabellare nel componente | `frontend/src/pages/SecurityDetailPage.tsx:80`, `:88` | Riepilogo trend e tabella dati accessibile |
| Media | Tabelle gestione portfolio/import molto larghe, da 736 a 1216 px minimi | `frontend/src/pages/PortfolioPage.tsx:1078`, `:1402`, `:1503`; `frontend/src/components/PortfolioImportPanel.tsx:56` | Riepilogo mobile e modifica di una posizione alla volta |

Contrasto della palette Tailwind standard: slate-500 (#64748b) su slate-950 (#020617) circa 4,24:1; su slate-900 (#0f172a) circa 3,75:1. Verificare i colori effettivi composti per ciascun elemento. Controlli da 28–38 px possono essere scomodi al tocco senza costituire automaticamente violazione AA.

## Aspetti positivi

Viewport corretto e zoom non bloccato, griglie con breakpoint, tabelle generalmente in contenitori scrollabili, molti form con label, button/link nativi, messaggi alert/status, header screener con scope e aria-sort, grafici ResponsiveContainer. Mancano invece skip link e gestione sistematica di focus/titolo dopo cambio pagina; dettaglio contiene un main annidato.

## Riferimenti e limiti

Riferimento: [WCAG 2.2 W3C](https://www.w3.org/TR/WCAG22/), in particolare nomi/ruoli dei controlli, tastiera, contrasto e reflow. [Reflow](https://www.w3.org/WAI/WCAG22/Understanding/reflow): tabelle bidimensionali possono rientrare nell'eccezione; la larghezza della tabella da sola non dimostra una violazione. I problemi di label e contrasto impediscono di presumere conformità AA, ma questo audit non misura una percentuale di conformità.

Passi successivi: correggere i problemi ad alta priorità, verificare flussi popolati con dati realistici, tastiera completa e VoiceOver/TalkBack, Safari iOS e Chrome Android, zoom e dimensioni testo, scansione automatica accessibilità seguita da verifica manuale.
