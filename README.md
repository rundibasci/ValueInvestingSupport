# Value Investing Advisory Platform — Technical Specification

**Version:** 1.0.0  
**Date:** 2026-06-07  
**Status:** Draft — Base per Spec-Driven Development Constitution  
**Primary Data Source:** Financial Modeling Prep (FMP) API  

---

## Table of Contents

1. [Vision & Scope](#1-vision--scope)
2. [Glossario](#2-glossario)
3. [Architettura di Sistema](#3-architettura-di-sistema)
4. [Data Source: FMP API](#4-data-source-fmp-api)
5. [Modelli di Dominio](#5-modelli-di-dominio)
6. [Moduli Funzionali](#6-moduli-funzionali)
7. [API interne (Backend)](#7-api-interne-backend)
8. [Regole di Business (Value Investing)](#8-regole-di-business-value-investing)
9. [Requisiti Non Funzionali](#9-requisiti-non-funzionali)
10. [Casi d'Uso Primari](#10-casi-duso-primari)
11. [Acceptance Criteria](#11-acceptance-criteria)
12. [Vincoli e Dipendenze](#12-vincoli-e-dipendenze)

---

## 1. Vision & Scope

### 1.1 Descrizione

Piattaforma software per consulenti finanziari e investitori privati che implementa il paradigma **Value Investing** basato sull'analisi dei fondamentali aziendali. Il sistema guida l'utente dalla scoperta di titoli sottovalutati fino alla costruzione e al monitoraggio di un portafoglio.

### 1.2 Paradigma Core

Il sistema si fonda sul ciclo Value Investing:

```
Screening → Analisi Fondamentale → Stima Valore Intrinseco
    → Calcolo Margine di Sicurezza → Raccomandazione
        → Costruzione Portafoglio → Monitoraggio Continuo
```

### 1.3 Utenti Target

| Ruolo | Descrizione |
|---|---|
| `ADVISOR` | Consulente finanziario che gestisce portafogli clienti |
| `INVESTOR` | Investitore privato self-directed |
| `ADMIN` | Amministratore di sistema |

### 1.4 Perimetro (MVP)

**In scope:**
- Screening fondamentale su universo azionario globale (FMP)
- Analisi fondamentale di singolo titolo (10 anni storici)
- Modelli di valutazione: DCF, Graham Number, DDM
- Calcolo e monitoraggio Margine di Sicurezza
- Costruzione portafoglio guidata (value-weighted)
- Dashboard e reporting cliente

**Out of scope (v1):**
- Trading / esecuzione ordini
- Derivati e opzioni
- Asset alternativi (PE, RE, crypto)
- Integrazione con custodian bank

---

## 2. Glossario

| Termine | Definizione |
|---|---|
| **Fair Value (FV)** | Stima del valore intrinseco di un titolo, calcolata dal Valuation Engine |
| **Margine di Sicurezza (MoS)** | `(FV - Prezzo) / FV × 100`. Positivo = titolo sottovalutato |
| **TTM** | Trailing Twelve Months — ultimi 12 mesi rolling |
| **DCF** | Discounted Cash Flow — modello di valutazione basato su flussi di cassa futuri attualizzati |
| **WACC** | Weighted Average Cost of Capital — tasso di attualizzazione del DCF |
| **FCF** | Free Cash Flow = Operating Cash Flow - Capital Expenditure |
| **Graham Number** | `√(22.5 × EPS × BVPS)` — valore intrinseco semplificato (Benjamin Graham) |
| **DDM** | Dividend Discount Model — valutazione basata su dividendi futuri |
| **Economic Moat** | Vantaggio competitivo sostenibile (Morningstar: None / Narrow / Wide) |
| **Value Score** | Score composito 0–100 calcolato internamente, aggregazione di più segnali value |
| **Piotroski F-Score** | Score 0–9 di solidità finanziaria (FMP: endpoint nativo) |
| **ROIC** | Return on Invested Capital = NOPAT / Invested Capital |
| **Watchlist** | Lista di titoli monitorati da un utente con alert personalizzati |

---

## 3. Architettura di Sistema

### 3.1 Stack Tecnologico

```
┌─────────────────────────────────────────────────────────┐
│                    FRONTEND                             │
│  React + TypeScript + TailwindCSS                       │
│  Recharts (grafici) | React Query (server state)        │
└────────────────────────┬────────────────────────────────┘
                         │ REST / JSON
┌────────────────────────▼────────────────────────────────┐
│                    BACKEND (API Gateway)                 │
│  Spring Boot 3.x + Java 21                              │
│  Spring Security (JWT) | Spring Cache (Redis)           │
└──────┬─────────────────┬──────────────────┬─────────────┘
       │                 │                  │
┌──────▼──────┐  ┌───────▼───────┐  ┌──────▼──────────────┐
│  FMP Client │  │ Valuation     │  │  Portfolio          │
│  (Data      │  │ Engine        │  │  Engine             │
│  Ingestion) │  │ (DCF/Graham/  │  │  (Allocation +      │
│             │  │  DDM/Score)   │  │   Rebalancing)      │
└──────┬──────┘  └───────────────┘  └─────────────────────┘
       │
┌──────▼──────────────────────────────────────────────────┐
│                    DATA LAYER                           │
│  PostgreSQL (fondamentali, portafogli, utenti)          │
│  Redis (cache FMP responses, calcoli DCF)               │
│  TimescaleDB / PostgreSQL partitioned (prezzi storici)  │
└─────────────────────────────────────────────────────────┘
```

### 3.2 Principi Architetturali

- **API-First**: ogni funzionalità esposta via REST prima di costruire il frontend
- **Cache-First per FMP**: tutti i dati FMP vengono cachati per ridurre costi API e latenza
- **Immutabilità dei dati storici**: i fondamentali storici una volta ingested non vengono sovrascritti
- **Separazione Dominio/Infrastruttura**: il Valuation Engine non dipende da FMP direttamente

---

## 4. Data Source: FMP API

### 4.1 Configurazione Base

```
Base URL:    https://financialmodelingprep.com/stable/
Auth:        Header: apikey: {API_KEY}
             oppure: ?apikey={API_KEY}
Format:      JSON
SLA:         99.9% uptime (enterprise plan)
Rate Limit:  dipende dal piano (vedere 4.2)
```

### 4.2 Piani e Limiti

| Piano | Costo/mese | Calls/min | Storico | Bulk | Note |
|---|---|---|---|---|---|
| Free | $0 | 10 | 5 anni | ❌ | Solo prototipo |
| Starter | ~$19 | 60 | 10 anni | ❌ | Dev/test |
| Premium | ~$49 | 300 | 30 anni | ✅ | MVP production |
| Ultimate | ~$149 | 750 | 30 anni | ✅ | Scaling |
| Enterprise | Custom | Custom | 30 anni | ✅ | SLA contrattuale |

> **Raccomandazione MVP:** Piano **Premium** ($49/mese). Copre tutti gli endpoint necessari con storico 30 anni e bulk API per screening.

### 4.3 Endpoint Utilizzati — Mappa Completa

#### 4.3.1 Company Search & Directory

| Endpoint | URL | Uso nel sistema |
|---|---|---|
| Symbol Search | `GET /stable/search-symbol?query={q}` | Autocomplete ticker |
| Name Search | `GET /stable/search-name?query={q}` | Ricerca per nome azienda |
| Stock Screener | `GET /stable/company-screener` | Screening fondamentale |
| Financial Symbols List | `GET /stable/financial-statement-symbol-list` | Universo titoli disponibili |
| ISIN Search | `GET /stable/search-isin?isin={isin}` | Lookup da ISIN (titoli EU) |

**Stock Screener — Parametri rilevanti per Value Investing:**

```
marketCapMoreThan      int       Capitalizzazione minima (es. 1000000000 = 1B)
marketCapLessThan      int       Capitalizzazione massima
priceMoreThan          float     Prezzo minimo
priceLessThan          float     Prezzo massimo
betaMoreThan           float     Beta minimo
betaLessThan           float     Beta massimo
volumeMoreThan         int       Volume minimo
dividendMoreThan       float     Dividend yield minimo (es. 0.02 = 2%)
isEtf                  bool      false per escludere ETF
isFund                 bool      false per escludere fondi
isActivelyTrading      bool      true per soli titoli attivi
sector                 string    es. "Technology", "Healthcare", "Consumer Defensive"
industry               string    es. "Drug Manufacturers"
country                string    es. "US", "IT", "GB"
exchange               string    es. "NASDAQ", "NYSE", "EURONEXT"
limit                  int       Max risultati (default 100, max 1000)
```

#### 4.3.2 Company Information

| Endpoint | URL | Dati restituiti |
|---|---|---|
| Company Profile | `GET /stable/profile/{symbol}` | Nome, settore, descrizione, CEO, dipendenti, paese, exchange, mktCap, prezzo, beta, volumi |
| Key Executives | `GET /stable/key-executives?symbol={symbol}` | Management team |
| Company Outlook | `GET /stable/company-outlook?symbol={symbol}` | Overview completo aggregato |
| Peers | `GET /stable/stock-peers?symbol={symbol}` | Lista ticker peer/competitors |
| Shares Float | `GET /stable/shares-float?symbol={symbol}` | Float, insider ownership, istituzionali |

**Profile — Campi chiave per Value Investing:**
```json
{
  "symbol": "AAPL",
  "companyName": "Apple Inc.",
  "sector": "Technology",
  "industry": "Consumer Electronics",
  "country": "US",
  "mktCap": 2800000000000,
  "price": 182.5,
  "beta": 1.24,
  "lastDividend": 0.96,
  "dcfDiff": 15.3,        // Differenza % prezzo vs DCF FMP
  "dcf": 210.5,           // Fair Value DCF calcolato da FMP
  "ipoDate": "1980-12-12",
  "isEtf": false,
  "isActivelyTrading": true
}
```

#### 4.3.3 Financial Statements

Tutti gli endpoint supportano i parametri: `symbol`, `period` (`annual`|`quarter`), `limit` (default 10, max 40).

| Endpoint | URL | Contenuto |
|---|---|---|
| Income Statement | `GET /stable/income-statement?symbol={s}&period=annual&limit=10` | Revenue, Gross Profit, EBITDA, Net Income, EPS |
| Balance Sheet | `GET /stable/balance-sheet-statement?symbol={s}&period=annual&limit=10` | Asset, Liabilities, Equity, Cash, Debt |
| Cash Flow | `GET /stable/cash-flow-statement?symbol={s}&period=annual&limit=10` | Operating CF, CapEx, Free Cash Flow, Dividends paid |
| Income Statement (TTM) | `GET /stable/income-statement-ttm?symbol={s}` | Dati rolling ultimi 12 mesi |
| Balance Sheet (TTM) | `GET /stable/balance-sheet-statement-ttm?symbol={s}` | Dati rolling |
| Cash Flow (TTM) | `GET /stable/cash-flow-statement-ttm?symbol={s}` | Dati rolling |
| As Reported Income | `GET /stable/as-reported-income-statements?symbol={s}` | Dati grezzi GAAP/IFRS non normalizzati |
| As Reported Balance | `GET /stable/as-reported-balance-sheet-statements?symbol={s}` | Dati grezzi |
| As Reported Cash Flow | `GET /stable/as-reported-cashflow-statements?symbol={s}` | Dati grezzi |

**Income Statement — Campi chiave:**
```json
{
  "date": "2024-09-28",
  "symbol": "AAPL",
  "reportedCurrency": "USD",
  "revenue": 391035000000,
  "costOfRevenue": 210352000000,
  "grossProfit": 180683000000,
  "grossProfitRatio": 0.4624,
  "operatingIncome": 123216000000,
  "ebitda": 134661000000,
  "netIncome": 93736000000,
  "eps": 6.08,
  "epsDiluted": 6.08,
  "weightedAverageShsOut": 15408095000,
  "interestExpense": 3804000000
}
```

**Cash Flow — Campi chiave:**
```json
{
  "date": "2024-09-28",
  "operatingCashFlow": 118254000000,
  "capitalExpenditure": -9447000000,
  "freeCashFlow": 108807000000,
  "dividendsPaid": -15234000000,
  "stockRepurchased": -94949000000,
  "netCashUsedForInvestingActivites": -29266000000
}
```

#### 4.3.4 Key Metrics & Ratios

| Endpoint | URL | Contenuto |
|---|---|---|
| Key Metrics | `GET /stable/key-metrics?symbol={s}&period=annual&limit=10` | Metriche composite (PE, ROIC, FCF yield, ecc.) |
| Key Metrics TTM | `GET /stable/key-metrics-ttm?symbol={s}` | Metriche TTM |
| Financial Ratios | `GET /stable/ratios?symbol={s}&period=annual&limit=10` | Ratios completi storici |
| TTM Ratios | `GET /stable/ratios-ttm?symbol={s}` | Ratios TTM (60+ metriche) |
| Financial Score | `GET /stable/score?symbol={s}` | Piotroski F-Score + Altman Z-Score |
| Owner Earnings | `GET /stable/owner-earnings?symbol={s}` | Buffett Owner Earnings |

**Key Metrics — Campi rilevanti per Value Investing:**
```json
{
  "date": "2024-09-28",
  "symbol": "AAPL",
  "peRatio": 30.01,
  "priceToBookRatio": 49.8,
  "priceToSalesRatio": 7.16,
  "evToEbitda": 23.4,
  "evToFreeCashFlow": 25.6,
  "priceToFreeCashFlowRatio": 25.8,
  "roe": 1.472,
  "roic": 0.548,
  "returnOnTangibleAssets": 0.316,
  "freeCashFlowYield": 0.0388,
  "debtToEquity": 145.1,
  "netDebtToEBITDA": 0.42,
  "currentRatio": 0.867,
  "dividendYield": 0.0053,
  "payoutRatio": 0.159,
  "revenuePerShare": 25.38,
  "freeCashFlowPerShare": 7.07,
  "bookValuePerShare": 3.77,
  "earningsYield": 0.0333,
  "grahamNumber": 9.51,
  "grahamNetNet": -14.6,
  "workingCapital": -26896000000,
  "enterpriseValue": 3117000000000,
  "marketCap": 2800000000000
}
```

**Financial Score — Campi:**
```json
{
  "symbol": "AAPL",
  "altmanZScore": 9.81,
  "piotroskiScore": 7,
  "workingCapital": -26896000000,
  "totalAssets": 364980000000,
  "retainedEarnings": -19154000000,
  "ebit": 123216000000,
  "marketCap": 2800000000000,
  "totalLiabilities": 308030000000,
  "revenue": 391035000000
}
```

#### 4.3.5 Valuation / DCF

| Endpoint | URL | Contenuto |
|---|---|---|
| DCF Valuation | `GET /stable/discounted-cash-flow?symbol={s}` | Fair Value DCF precalcolato FMP |
| Advanced DCF | `GET /stable/advanced-discounted-cash-flow?symbol={s}` | DCF con dettaglio scenari |
| Levered DCF | `GET /stable/levered-discounted-cash-flow?symbol={s}` | DCF post-debt |
| Company Rating | `GET /stable/rating?symbol={s}` | Rating sintetico FMP (A/B/C/D/F) |

**DCF Response:**
```json
{
  "symbol": "AAPL",
  "date": "2026-06-07",
  "dcf": 210.50,
  "Stock Price": 182.50
}
```

> **Nota**: Il DCF FMP è precalcolato con parametri standard. Il sistema utilizzerà anche un **DCF Engine interno** con parametri configurabili (WACC, growth rate, terminal rate) — vedere sezione 8.

#### 4.3.6 Growth & Trend

| Endpoint | URL | Contenuto |
|---|---|---|
| Income Statement Growth | `GET /stable/income-statement-growth?symbol={s}&limit=10` | CAGR Revenue, Net Income, EPS YoY |
| Balance Sheet Growth | `GET /stable/balance-sheet-statement-growth?symbol={s}&limit=10` | Crescita Asset, Equity, Debt |
| Cash Flow Growth | `GET /stable/cashflow-statement-growth?symbol={s}&limit=10` | Crescita FCF, CapEx, Dividendi |
| Financial Growth | `GET /stable/financial-growth?symbol={s}&limit=10` | Crescita aggregata multi-metrica |

**Financial Growth — Campi chiave:**
```json
{
  "date": "2024-09-28",
  "symbol": "AAPL",
  "revenueGrowth": 0.0229,
  "grossProfitGrowth": 0.0990,
  "ebitgrowth": 0.1044,
  "operatingIncomeGrowth": 0.1044,
  "netIncomeGrowth": 0.1093,
  "epsgrowth": 0.1207,
  "freeCashFlowGrowth": 0.1925,
  "dividendsperShareGrowth": 0.0417,
  "bookValueperShareGrowth": -0.2740,
  "debtGrowth": -0.0234,
  "rdexpenseGrowth": 0.0523,
  "sgaexpensesGrowth": 0.0467
}
```

#### 4.3.7 Dividendi e Calendar

| Endpoint | URL | Contenuto |
|---|---|---|
| Dividend History | `GET /stable/dividends?symbol={s}` | Storico dividendi con date, importi, yield |
| Dividend Calendar | `GET /stable/dividends-calendar?from={date}&to={date}` | Dividendi in arrivo per data |
| Earnings History | `GET /stable/earnings?symbol={s}` | EPS reale vs stima, sorprese |
| Earnings Calendar | `GET /stable/earnings-calendar?from={date}&to={date}` | Earnings call in arrivo |
| Stock Split History | `GET /stable/stock-splits?symbol={s}` | Storico split azionari |

**Dividend — Campi chiave:**
```json
{
  "symbol": "AAPL",
  "date": "2024-11-08",
  "dividend": 0.25,
  "recordDate": "2024-11-11",
  "paymentDate": "2024-11-14",
  "declarationDate": "2024-10-28",
  "adjDividend": 0.25
}
```

#### 4.3.8 Analyst Estimates & Price Targets

| Endpoint | URL | Contenuto |
|---|---|---|
| Analyst Estimates | `GET /stable/analyst-estimates?symbol={s}&period=annual&limit=5` | Consensus EPS/Revenue futuri |
| Price Target | `GET /stable/price-target?symbol={s}` | Price target analisti sell-side |
| Price Target Consensus | `GET /stable/price-target-consensus?symbol={s}` | Consensus (min/median/max) |
| Price Target RSS | `GET /stable/price-target-rss-feed?page=0` | Feed aggiornamenti price target |
| Analyst Recommendation | `GET /stable/analyst-stock-recommendations?symbol={s}` | Buy/Hold/Sell consensus |
| Upgrades Downgrades | `GET /stable/upgrades-downgrades?symbol={s}` | Variazioni rating analisti |

**Price Target Consensus:**
```json
{
  "symbol": "AAPL",
  "targetHigh": 260.00,
  "targetLow": 170.00,
  "targetConsensus": 215.00,
  "targetMedian": 220.00
}
```

#### 4.3.9 Insider & Institutional Ownership

| Endpoint | URL | Contenuto |
|---|---|---|
| Insider Trading | `GET /stable/insider-trading?symbol={s}&limit=20` | Acquisti/vendite da insider |
| Insider Trading RSS | `GET /stable/insider-trading-rss-feed?page=0` | Feed aggiornamenti insider |
| Institutional Holders | `GET /stable/institutional-holder?symbol={s}` | Fondi e istituzioni che detengono il titolo |
| Mutual Fund Holders | `GET /stable/mutual-fund-holder?symbol={s}` | Fondi comuni holder |
| Form 13F | `GET /stable/form-thirteen-f?cik={cik}&date={date}` | Portafogli gestori 13F |

#### 4.3.10 SEC Filings

| Endpoint | URL | Contenuto |
|---|---|---|
| SEC Filings List | `GET /stable/sec-filings?symbol={s}&type=10-K&limit=5` | Lista filing (10-K, 10-Q, 8-K) |
| Annual Report 10-K | `GET /stable/annual-reports-on-form-10-k?symbol={s}` | Link ai filing annuali |
| SEC RSS Feed | `GET /stable/rss-feed-8k?page=0` | Feed 8-K (eventi materiali) |

#### 4.3.11 ESG

| Endpoint | URL | Contenuto |
|---|---|---|
| ESG Score | `GET /stable/esg-environmental-social-governance-data?symbol={s}` | Punteggio ESG (E, S, G) |
| ESG Rating | `GET /stable/esg-environmental-social-governance-sector-rating?sector={s}` | Rating per settore |

#### 4.3.12 Bulk API (Piano Premium+)

| Endpoint | URL | Contenuto |
|---|---|---|
| Bulk Profiles | `GET /stable/profile?exchange=NYSE` | Profili massivi per exchange |
| Bulk Key Metrics TTM | `GET /stable/key-metrics-ttm-bulk?exchange=NYSE` | Metriche TTM per tutti i titoli |
| Bulk Ratios TTM | `GET /stable/ratios-ttm-bulk?exchange=NYSE` | Ratios TTM in bulk |
| Bulk Income Statement | `GET /stable/income-statement-bulk?exchange=NYSE&period=annual` | Bilanci in bulk |
| Bulk DCF | `GET /stable/discounted-cash-flow-bulk` | DCF per tutti i titoli |

> **Uso**: le Bulk API alimentano il database locale notturno. Lo screener lavora sempre sul DB locale, non chiamando FMP on-demand.

#### 4.3.13 Market Data

| Endpoint | URL | Contenuto |
|---|---|---|
| Quote | `GET /stable/quote/{symbol}` | Prezzo real-time, volume, change% |
| Historical Price | `GET /stable/historical-price-full/{symbol}?from={d}&to={d}` | OHLCV giornaliero |
| Market Hours | `GET /stable/market-hours` | Orari di apertura per exchange |
| Sector Performance | `GET /stable/sector-performance` | Performance settori |
| Market Movers | `GET /stable/stock_market/gainers` | Top gainers/losers |

### 4.4 Strategia di Caching

```
┌─────────────────────────────────────────────────────────┐
│              CACHE TTL PER TIPO DI DATO                 │
├──────────────────────────────────┬──────────────────────┤
│ Prezzi real-time (Quote)         │ 15 minuti            │
│ Fondamentali (annual)            │ 24 ore               │
│ Fondamentali (quarterly/TTM)     │ 6 ore                │
│ Company Profile                  │ 24 ore               │
│ DCF precalcolato FMP             │ 24 ore               │
│ DCF Engine interno               │ 1 ora (post ricalcolo)│
│ Analyst estimates                │ 6 ore                │
│ Dividend history                 │ 24 ore               │
│ Insider trading                  │ 1 ora                │
│ Bulk screener data               │ 24 ore (refresh notte)│
└──────────────────────────────────┴──────────────────────┘
```

**Implementazione:** Redis con namespace `fmp:{endpoint}:{symbol}:{params_hash}`.

### 4.5 Error Handling FMP

```java
// Codici di errore da gestire
429  Too Many Requests    → exponential backoff + retry (max 3)
401  Unauthorized         → alert admin, usa cache se disponibile  
404  Symbol not found     → marca simbolo come non disponibile
500  FMP Server Error     → fallback a cache, alert monitoring
503  Service Unavailable  → fallback completo a dati cached
```

---

## 5. Modelli di Dominio

### 5.1 Entità Principali

```
Security
  ├── symbol: String (PK)
  ├── name: String
  ├── exchange: String
  ├── sector: String
  ├── industry: String
  ├── country: String
  ├── currency: String
  ├── isActivelyTrading: Boolean
  └── lastUpdated: Timestamp

FundamentalSnapshot  (una riga per symbol+period+date)
  ├── symbol: String (FK → Security)
  ├── period: Enum {ANNUAL, QUARTER, TTM}
  ├── fiscalDate: Date
  ├── revenue: BigDecimal
  ├── grossProfit: BigDecimal
  ├── operatingIncome: BigDecimal
  ├── ebitda: BigDecimal
  ├── netIncome: BigDecimal
  ├── eps: BigDecimal
  ├── epsDiluted: BigDecimal
  ├── operatingCashFlow: BigDecimal
  ├── capitalExpenditure: BigDecimal
  ├── freeCashFlow: BigDecimal
  ├── dividendsPaid: BigDecimal
  ├── totalDebt: BigDecimal
  ├── totalEquity: BigDecimal
  ├── cashAndEquivalents: BigDecimal
  └── reportedCurrency: String

RatioSnapshot  (una riga per symbol+period+date)
  ├── symbol: String (FK)
  ├── period: Enum
  ├── fiscalDate: Date
  ├── peRatio: BigDecimal
  ├── priceToBookRatio: BigDecimal
  ├── evToEbitda: BigDecimal
  ├── priceToFreeCashFlow: BigDecimal
  ├── roe: BigDecimal
  ├── roic: BigDecimal
  ├── debtToEquity: BigDecimal
  ├── currentRatio: BigDecimal
  ├── dividendYield: BigDecimal
  ├── payoutRatio: BigDecimal
  ├── freeCashFlowYield: BigDecimal
  └── piotroskiScore: Integer

ValuationResult  (generato dal Valuation Engine)
  ├── id: UUID
  ├── symbol: String (FK)
  ├── calculatedAt: Timestamp
  ├── priceAtCalculation: BigDecimal
  ├── model: Enum {DCF, GRAHAM, DDM, FMP_DCF, PEER}
  ├── fairValue: BigDecimal
  ├── fairValueLow: BigDecimal   (scenario pessimistico)
  ├── fairValueHigh: BigDecimal  (scenario ottimistico)
  ├── marginOfSafety: BigDecimal (%)
  ├── parameters: JSONB          (WACC, growth rates, ecc.)
  └── computedBy: String         (user o sistema)

ValueScore  (score composito per symbol)
  ├── symbol: String
  ├── calculatedAt: Timestamp
  ├── totalScore: Integer (0–100)
  ├── mosScore: Integer (0–30)      // Margine di Sicurezza
  ├── qualityScore: Integer (0–25)  // FCF quality, ROIC
  ├── safetyScore: Integer (0–20)   // Piotroski, debt
  ├── growthScore: Integer (0–15)   // Trend fondamentali
  ├── dividendScore: Integer (0–10) // Dividend safety
  └── weights: JSONB

Portfolio
  ├── id: UUID
  ├── userId: UUID
  ├── name: String
  ├── currency: String
  ├── targetYield: BigDecimal
  ├── riskProfile: Enum {CONSERVATIVE, BALANCED, GROWTH}
  └── createdAt: Timestamp

Holding
  ├── id: UUID
  ├── portfolioId: UUID (FK)
  ├── symbol: String (FK)
  ├── shares: BigDecimal
  ├── averageCostBasis: BigDecimal
  ├── currentWeight: BigDecimal
  ├── targetWeight: BigDecimal
  └── lastUpdated: Timestamp

Watchlist
  ├── id: UUID
  ├── userId: UUID
  ├── symbol: String
  ├── alertMosMin: BigDecimal      // Alert se MoS scende sotto soglia
  ├── alertMosMax: BigDecimal      // Alert se titolo > fair value di X%
  ├── alertFundamentalDegrade: Boolean
  ├── userFairValue: BigDecimal    // Override manuale Fair Value
  └── notes: Text
```

---

## 6. Moduli Funzionali

### 6.1 MOD-01: Data Ingestion

**Responsabilità:** Sincronizzare dati FMP nel DB locale.

**Processi:**

| Job | Frequenza | Endpoint FMP | Tabella target |
|---|---|---|---|
| Bulk Profile Sync | Giornaliero 02:00 | `/stable/profile?exchange=*` | `security` |
| Bulk Fundamentals Sync | Giornaliero 03:00 | `/stable/income-statement-bulk` ecc. | `fundamental_snapshot` |
| Bulk Ratios Sync | Giornaliero 03:30 | `/stable/ratios-ttm-bulk` | `ratio_snapshot` |
| Bulk DCF Sync | Giornaliero 04:00 | `/stable/discounted-cash-flow-bulk` | `fmp_dcf` |
| Quote Refresh | Ogni 15 minuti | `/stable/quote/{symbol}` | `price_quote` |
| Dividend Update | Giornaliero 06:00 | `/stable/dividends` (watchlist + holdings) | `dividend_history` |
| Insider Feed | Ogni ora | `/stable/insider-trading-rss-feed` | `insider_trade` |

### 6.2 MOD-02: Stock Screener

**Responsabilità:** Filtrare l'universo azionario con criteri value investing.

**Input — ScreenerRequest:**
```json
{
  "filters": {
    "sectors": ["Healthcare", "Consumer Defensive"],
    "countries": ["US", "GB"],
    "exchanges": ["NYSE", "NASDAQ"],
    "marketCapMin": 1000000000,
    "marketCapMax": 500000000000,
    "peRatioMax": 20,
    "priceToBookMax": 3.0,
    "evToEbitdaMax": 12,
    "roeMin": 0.12,
    "roicMin": 0.10,
    "debtToEquityMax": 1.5,
    "currentRatioMin": 1.0,
    "dividendYieldMin": 0.02,
    "payoutRatioMax": 0.75,
    "fcfYieldMin": 0.03,
    "piotroskiScoreMin": 5,
    "marginOfSafetyMin": 10,
    "revenueGrowth5yMin": 0.03
  },
  "sortBy": "valueScore",
  "sortDirection": "DESC",
  "page": 0,
  "pageSize": 50
}
```

**Output — ScreenerResult per titolo:**
```json
{
  "symbol": "ABT",
  "name": "Abbott Laboratories",
  "sector": "Healthcare",
  "price": 104.50,
  "marketCap": 181000000000,
  "valueScore": 78,
  "marginOfSafety": 13.6,
  "fairValue": 121.0,
  "peRatio": 22.1,
  "priceToBook": 4.2,
  "evToEbitda": 16.8,
  "roe": 0.168,
  "roic": 0.124,
  "dividendYield": 0.021,
  "payoutRatio": 0.52,
  "piotroskiScore": 7,
  "debtToEquity": 0.58,
  "fcfYield": 0.041
}
```

### 6.3 MOD-03: Security Detail (Analisi Fondamentale)

**Responsabilità:** Vista completa di un singolo titolo con tutti i dati storici e le valutazioni.

**Sotto-sezioni della scheda titolo:**

- **Overview**: profilo, descrizione, management, peer group
- **Financials**: income, balance, cash flow — ultimi 10 anni annual + 8 quarter TTM
- **Ratios & Quality**: trend P/E, P/FCF, ROIC, ROE, Piotroski su 10 anni
- **Valuation**: DCF custom, Graham Number, DDM, confronto con FMP DCF, margine di sicurezza
- **Dividendi**: storico dividendi, crescita annua, payout ratio, streak anni consecutivi
- **Growth**: CAGR Revenue/FCF/EPS a 3, 5, 10 anni
- **Insider & Ownership**: operazioni recenti insider, composizione azionariato
- **Analyst**: consensus, price target, upgrade/downgrade recenti
- **ESG**: score E, S, G con confronto settoriale
- **Documenti**: link a 10-K, 10-Q più recenti

### 6.4 MOD-04: Valuation Engine

**Responsabilità:** Calcolo del valore intrinseco con modelli multipli.

**Modello DCF (parametri input):**
```
freeCashFlow_ttm     BigDecimal    FCF TTM (da FMP)
growthRate_y1_y5     double        Tasso crescita anni 1–5 (default: FCF CAGR 5y)
growthRate_y6_y10    double        Tasso crescita anni 6–10 (default: FCF CAGR 5y × 0.6)
terminalGrowthRate   double        Tasso terminale (default: 2.5%)
wacc                 double        Tasso sconto (default: 8–10% per settore)
shares               long          Shares outstanding
netDebt              BigDecimal    Net debt (per calcolo equity value)
```

**Formula DCF:**
```
PV_i = FCF_0 × (1 + g1)^i / (1 + WACC)^i     [anni 1–5]
PV_i = FCF_5 × (1 + g2)^(i-5) / (1 + WACC)^i  [anni 6–10]
TV   = FCF_10 × (1 + gT) / (WACC - gT)
TV_discounted = TV / (1 + WACC)^10
Enterprise Value = Σ PV_i + TV_discounted
Equity Value = EV - NetDebt
Fair Value per Share = Equity Value / Shares
```

**Modello Graham Number:**
```
GrahamNumber = √(22.5 × EPS_TTM × BVPS)
```

**Modello DDM (Gordon Growth — per dividend stocks):**
```
FairValue = DPS_next / (Ke - g)
dove:
  DPS_next = DPS_TTM × (1 + g)
  Ke       = Required rate of return (input utente, default 7%)
  g        = Dividend growth rate (CAGR dividendi 5y)
```

**Fair Value Composito (weighted average):**
```
FV_composite = w_dcf × FV_dcf + w_graham × FV_graham + w_ddm × FV_ddm
```
I pesi sono configurabili; default: DCF 60%, Graham 25%, DDM 15% (se dividendo disponibile).

### 6.5 MOD-05: Value Score Engine

**Responsabilità:** Calcolo dello score composito 0–100 per ogni titolo.

```
ValueScore = mosScore + qualityScore + safetyScore + growthScore + dividendScore

mosScore (0–30):
  MoS > 30%   → 30 pt
  MoS 20–30%  → 24 pt
  MoS 10–20%  → 18 pt
  MoS 0–10%   → 9 pt
  MoS < 0     → 0 pt

qualityScore (0–25):
  ROIC > 20%  → +10; 15–20% → +7; 10–15% → +5; <10% → +2
  FCF Yield > 5% → +8; 3–5% → +5; <3% → +2
  ROE > 15%   → +7; 10–15% → +5; <10% → +2

safetyScore (0–20):
  Piotroski 7–9 → +10; 5–6 → +7; 3–4 → +4; <3 → 0
  Debt/Equity < 0.5 → +5; 0.5–1 → +3; 1–1.5 → +1; >1.5 → 0
  Current Ratio > 1.5 → +5; 1–1.5 → +3; <1 → 0

growthScore (0–15):
  FCF CAGR 5y > 10% → +8; 5–10% → +5; 0–5% → +3; negativo → 0
  Revenue CAGR 5y > 7% → +7; 3–7% → +4; 0–3% → +2; negativo → 0

dividendScore (0–10):
  Solo se dividendo presente:
  Dividend Streak > 25y → +5; 10–25y → +3; 5–10y → +2; <5y → +1
  Payout Ratio < 50% → +3; 50–70% → +2; 70–85% → +1; >85% → 0
  DPS Growth 5y > 5% → +2; positivo → +1; 0 o negativo → 0
```

### 6.6 MOD-06: Portfolio Builder

**Responsabilità:** Costruzione guidata del portafoglio con ottimizzazione value.

**Logica di allocazione:**
- Input: budget totale, profilo rischio, settori preferiti, yield target
- Sistema propone i top N titoli per Value Score con MoS > soglia minima
- Pesi calcolati proporzionalmente al Value Score normalizzato
- Vincoli: max 20% per singolo titolo, max 40% per settore, max 50% per singolo paese
- Output: portafoglio proposto con peso %, shares consigliate, costo totale, yield medio ponderato

### 6.7 MOD-07: Alert & Monitoring

**Responsabilità:** Monitoraggio continuo e notifiche.

**Tipi di alert:**

| Codice | Trigger | Priorità |
|---|---|---|
| `MOS_BELOW_MIN` | Margine di Sicurezza scende sotto soglia configurata | HIGH |
| `MOS_ABOVE_FAIR` | Prezzo supera Fair Value di >15% | MEDIUM |
| `FUNDAMENTAL_DEGRADE` | FCF negativo o taglio dividendo | HIGH |
| `PIOTROSKI_DROP` | Piotroski Score scende di ≥2 punti | MEDIUM |
| `INSIDER_SELL_CLUSTER` | ≥3 vendite insider in 30 giorni | MEDIUM |
| `EARNINGS_MISS` | EPS reale < stima di >10% | LOW |
| `PRICE_TARGET_CUT` | Price target consensus tagliato di >15% | LOW |
| `DIVIDEND_CUT` | DPS ridotto rispetto al periodo precedente | HIGH |

---

## 7. API Interne (Backend)

### 7.1 Autenticazione

```
POST /auth/login          → JWT access token (15 min) + refresh token (7 giorni)
POST /auth/refresh        → Nuovo access token
POST /auth/logout         → Revoca refresh token
```

### 7.2 Screener

```
POST /api/v1/screener                → Esegui screening con filtri
GET  /api/v1/screener/presets        → Lista filtri preset (Graham, Dividend, Quality)
GET  /api/v1/screener/sectors        → Lista settori disponibili
GET  /api/v1/screener/exchanges      → Lista exchange disponibili
```

### 7.3 Security

```
GET  /api/v1/securities/search?q={q}              → Autocomplete titoli
GET  /api/v1/securities/{symbol}                  → Scheda completa titolo
GET  /api/v1/securities/{symbol}/financials       → Fondamentali storici
GET  /api/v1/securities/{symbol}/ratios           → Ratios storici
GET  /api/v1/securities/{symbol}/valuation        → Tutte le valutazioni
POST /api/v1/securities/{symbol}/valuation/dcf    → Calcola DCF custom
GET  /api/v1/securities/{symbol}/dividends        → Storico dividendi
GET  /api/v1/securities/{symbol}/insiders         → Insider trading
GET  /api/v1/securities/{symbol}/growth           → Metriche crescita
GET  /api/v1/securities/{symbol}/score            → Value Score dettagliato
GET  /api/v1/securities/{symbol}/peers            → Peer comparison
```

### 7.4 Watchlist

```
GET    /api/v1/watchlist                          → Lista watchlist utente
POST   /api/v1/watchlist                          → Aggiungi titolo
PUT    /api/v1/watchlist/{symbol}                 → Aggiorna alert/note
DELETE /api/v1/watchlist/{symbol}                 → Rimuovi titolo
GET    /api/v1/watchlist/alerts                   → Alert attivi
```

### 7.5 Portfolio

```
GET    /api/v1/portfolios                         → Lista portafogli
POST   /api/v1/portfolios                         → Crea portafoglio
GET    /api/v1/portfolios/{id}                    → Dettaglio portafoglio
POST   /api/v1/portfolios/{id}/simulate           → Simula portafoglio proposto
POST   /api/v1/portfolios/{id}/holdings           → Aggiungi holding
PUT    /api/v1/portfolios/{id}/holdings/{symbol}  → Aggiorna holding
DELETE /api/v1/portfolios/{id}/holdings/{symbol}  → Rimuovi holding
GET    /api/v1/portfolios/{id}/report             → Report PDF
GET    /api/v1/portfolios/{id}/rebalance          → Suggerimento ribilanciamento
```

---

## 8. Regole di Business (Value Investing)

### 8.1 Regole Inviolabili (Hard Rules)

```
RULE-01: Un titolo non può essere raccomandato se MoS < 0 (prezzo > Fair Value)
RULE-02: Un titolo con Piotroski Score < 3 viene marcato come "Financial Risk"
RULE-03: Un titolo con Payout Ratio > 100% viene marcato come "Dividend Unsustainable"
RULE-04: Un portafoglio non può avere un singolo titolo > 25% del totale
RULE-05: Un portafoglio non può avere un singolo settore > 40% del totale
RULE-06: Il DCF richiede almeno 3 anni di FCF positivo per essere calcolato
RULE-07: Il DDM richiede almeno 5 anni consecutivi di dividendi
```

### 8.2 Regole di Warning (Soft Rules — Alert)

```
WARN-01: Debt/Equity > 2.0 → "High Leverage Warning"
WARN-02: Current Ratio < 0.8 → "Liquidity Warning"
WARN-03: Revenue growth negativo per 2+ anni consecutivi → "Revenue Declining"
WARN-04: FCF negativo per 2+ anni consecutivi → "Cash Flow Negative"
WARN-05: DPS growth = 0 per 3+ anni → "Dividend Stagnant"
WARN-06: Insider selling > 3 transazioni in 30 giorni → "Insider Selling Alert"
WARN-07: Concentrazione settore > 30% in portafoglio → "Sector Concentration"
```

### 8.3 Classificazione Titoli per Strategia Value

| Categoria | Criteri | Strategia |
|---|---|---|
| **Deep Value** | MoS > 30%, P/B < 1.5, Piotroski ≥ 5 | Acquisto aggressivo |
| **Quality Value** | MoS 15–30%, ROIC > 15%, Moat Wide | Accumulazione progressiva |
| **Dividend Value** | MoS > 10%, Yield > 3%, Payout < 70%, Streak > 10y | DCA periodico |
| **Watchlist** | MoS 5–15% | Monitoraggio, no acquisto |
| **Fairly Valued** | MoS -5% – +5% | Hold se già in portafoglio |
| **Overvalued** | MoS < -5% | Considerare riduzione |

---

## 9. Requisiti Non Funzionali

| ID | Categoria | Requisito | Metrica |
|---|---|---|---|
| NFR-01 | Performance | Risposta API screener | < 500ms (dati da DB locale) |
| NFR-02 | Performance | Caricamento scheda titolo | < 1s (cache Redis) |
| NFR-03 | Performance | Calcolo DCF custom | < 200ms |
| NFR-04 | Scalabilità | Utenti concorrenti | 500 senza degradazione |
| NFR-05 | Disponibilità | Uptime sistema | 99.5% mensile |
| NFR-06 | Sicurezza | Autenticazione | JWT RS256, MFA opzionale |
| NFR-07 | Sicurezza | API Key FMP | Rotazione semestrale, vault |
| NFR-08 | Dati | Freschezza fondamentali | Max 24h dalla pubblicazione FMP |
| NFR-09 | Dati | Stale fallback | Servire cache anche se FMP down |
| NFR-10 | Compliance | API FMP | Rispettare rate limit piano scelto |
| NFR-11 | Compliance | Ridistribuzione dati | Accordo Data Display License con FMP |
| NFR-12 | Osservabilità | Logging | Structured JSON (Logback → ELK) |
| NFR-13 | Osservabilità | Metriche | Prometheus + Grafana |
| NFR-14 | Osservabilità | FMP API calls | Monitoraggio quota consumed/disponibile |

---

## 10. Casi d'Uso Primari

### UC-01: Screening Value (flusso principale)

```
Precondizioni: utente autenticato, bulk data aggiornato
Attore: ADVISOR o INVESTOR

1. Utente apre screener
2. Seleziona filtri (o sceglie preset "Quality Value")
3. Sistema interroga DB locale (non FMP diretto)
4. Ritorna lista ordinata per Value Score
5. Utente applica filtri aggiuntivi in real-time
6. Utente clicca su titolo → naviga a Security Detail

Flusso alternativo A: nessun risultato
  3a. Sistema allarga automaticamente i criteri e mostra warning

Postcondizioni: lista risultati salvabile come "Custom Preset"
```

### UC-02: Deep Dive con DCF Custom

```
Precondizioni: titolo selezionato dallo screener
Attore: ADVISOR

1. Utente apre scheda titolo
2. Sistema mostra fondamentali pre-caricati + DCF FMP (quick view)
3. Utente clicca "Calcola DCF Custom"
4. Sistema mostra form con valori precompilati (FCF TTM, CAGR storici)
5. Utente modifica WACC, growth rate, terminal rate
6. Sistema calcola DCF, Graham Number, DDM in parallelo
7. Mostra Fair Value composito con range (pessimistico/base/ottimistico)
8. Calcola Margine di Sicurezza vs prezzo corrente
9. Utente salva analisi → aggiunge a Watchlist con alert MoS
```

### UC-03: Costruzione Portafoglio Guidata

```
Precondizioni: watchlist con almeno 5 titoli con MoS > 0
Attore: ADVISOR

1. Utente apre Portfolio Builder
2. Inserisce: budget €50.000, profilo Balanced, target yield 3%
3. Sistema propone allocazione:
   - Filtra watchlist per MoS > 10% e Value Score > 65
   - Calcola pesi proporzionali a Value Score normalizzato
   - Verifica vincoli (25% max per titolo, 40% max per settore)
4. Mostra portafoglio proposto con: pesi %, shares, costo, yield medio
5. Advisor aggiusta manualmente i pesi
6. Sistema ricalcola in real-time: yield, concentrazione, MoS medio portafoglio
7. Advisor approva → portafoglio salvato

Postcondizioni: monitoring automatico attivato su tutti i titoli
```

### UC-04: Alert e Revisione

```
Attore: Sistema (automatico) + ADVISOR (revisione)

1. Job notturno aggiorna prezzi e ricalcola MoS per tutti i titoli in watchlist/portafoglio
2. Per titolo ABT: prezzo sale, MoS scende da 13.6% a 4.8% (sotto soglia 5%)
3. Sistema genera alert MOS_BELOW_MIN
4. Advisor riceve notifica (email + in-app)
5. Apre scheda ABT: vede MoS attuale, trend, ultimo DCF
6. Decide: conferma hold, o aggiorna Fair Value, o riduce posizione
7. Azione loggata nell'audit trail del portafoglio
```

---

## 11. Acceptance Criteria

### AC-01: Screener

```gherkin
Given un universo di 5000+ titoli in DB
When utente applica filtri: PE < 18, ROE > 12%, DivYield > 2%, Sector = Healthcare
Then risposta < 500ms
And risultati contengono solo titoli che soddisfano tutti i filtri
And ogni risultato ha: symbol, name, price, valueScore, marginOfSafety, peRatio, roe, dividendYield
And risultati ordinati per valueScore DESC
```

### AC-02: DCF Custom

```gherkin
Given simbolo "ABT" con FCF positivo negli ultimi 5 anni
When utente richiede DCF con WACC=8%, growthY1Y5=7%, growthY6Y10=4%, terminal=2.5%
Then sistema calcola Fair Value in < 200ms
And Fair Value è calcolato secondo formula DCF documentata in sezione 6.4
And Margine di Sicurezza = (FairValue - PrezzoCorrente) / FairValue × 100
And risultato include scenario pessimistico (WACC+2%) e ottimistico (WACC-1%)
```

### AC-03: Value Score

```gherkin
Given titolo con: MoS=25%, ROIC=18%, FCFYield=4.5%, Piotroski=7, DivYield=2.8%, PayoutRatio=45%, DivStreak=20y, FCF CAGR5y=9%
When sistema calcola ValueScore
Then mosScore = 24 (MoS 20–30%)
And qualityScore = 17 (ROIC 15–20%=7, FCFYield 3–5%=5, ROE assume >15%=5)
And safetyScore = 15 (Piotroski 7–9=10, DebtEquity assume <0.5=5)
And growthScore = 13 (FCF CAGR 5–10%=5+RevCAGR assume 3–7%=4... adattare ai dati)
And dividendScore = 8 (Streak 10–25y=3, PayoutRatio<50%=3, DPS growth>5%=2)
And totalScore è la somma dei parziali
```

### AC-04: Alert

```gherkin
Given titolo "ABT" in watchlist con alertMosMin = 5.0
When prezzo di "ABT" supera fair value causando MoS = 4.5%
Then sistema genera alert di tipo MOS_BELOW_MIN entro 15 minuti dall'aggiornamento prezzi
And alert contiene: symbol, currentMoS, threshold, currentPrice, fairValue, timestamp
And utente riceve notifica via canale configurato (email / in-app)
```

---

## 12. Vincoli e Dipendenze

### 12.1 Dipendenze Esterne

| Dipendenza | Versione/Piano | Rischio | Mitigazione |
|---|---|---|---|
| FMP API | Premium+ | MEDIUM — prezzi possono cambiare | Contratto annuale, cache aggressiva |
| FMP Data License | Display License | HIGH — obbligatoria per redistribuzione | Accordo legale da firmare prima del go-live |
| Redis | 7.x | LOW | Cluster HA, fallback a DB |
| PostgreSQL | 16.x | LOW | Managed cloud (CloudSQL/RDS) |

### 12.2 Vincoli Legali e Compliance

- **FMP Terms of Service**: i dati FMP non possono essere redistribuiti raw. Il sistema mostra dati elaborati/aggregati nella UI; non espone endpoint pubblici che ritornano dati FMP grezzi.
- **MiFID II**: le valutazioni prodotte dal sistema sono strumenti di supporto alle decisioni, non raccomandazioni d'investimento ai sensi MiFID. Il disclaimer è obbligatorio in ogni schermata che mostra Fair Value o Value Score.
- **GDPR**: i dati degli utenti (portafogli, watchlist) sono trattati in conformità GDPR. Nessun dato cliente è trasmesso a FMP.

### 12.3 Assunzioni

- L'universo screener copre principalmente titoli USA (NYSE, NASDAQ) e principali borse europee (EURONEXT, LSE, MTA).
- Il Fair Value composito utilizza il piano FMP Premium che include endpoint DCF e bulk API.
- Il sistema non esegue ordini: è read-only rispetto ai mercati.
- I calcoli di valutazione sono effettuati in USD; la conversione in EUR è applicata al layer di presentazione.

---

*Documento preparato come base per la Spec-Driven Development Constitution.*  
*Prossimo step: tradurre questa specifica in API contracts (OpenAPI 3.1), entity schemas (JSON Schema), e test specifications.*
