# Mission — Value Investing Advisory Platform

## Purpose

Build a software platform that guides financial advisors and self-directed investors through the full Value Investing cycle: from discovering undervalued stocks to constructing and monitoring a model portfolio.

## Core Value Proposition

> Given a universe of thousands of publicly traded companies, surface the handful that are fundamentally sound, competitively advantaged, and priced below their intrinsic value — then help the user build and manage a portfolio of them.

## Value Investing Cycle (the system's spine)

```
Market Universe Seeding → Screening / Research → Fundamental Analysis → Intrinsic Value Estimation
    → Margin of Safety Calculation → Recommendation
        → Portfolio Construction → Continuous Monitoring
```

Every feature must map to one or more steps in this cycle. The **Recommendation** step may be augmented — never replaced — by an AI-generated investment thesis (bull case, bear case, risks, invalidation conditions) built on Google Cloud Vertex AI's Gemini API; see Design Principle 15 and `specs/roadmap.md` → Group TA.

## Users

| Role | Responsibility |
|---|---|
| `ADVISOR` | Financial advisor managing client portfolios |
| `INVESTOR` | Self-directed private investor |
| `ADMIN` | System administrator |

## Design Principles

1. **Data before opinion** — every recommendation is grounded in financial fundamentals, not sentiment.
2. **Transparency** — all valuation outputs include their input parameters and the formula used; nothing is a black box.
3. **Conservative defaults** — when in doubt, use the more pessimistic assumption (higher WACC, lower growth rate).
4. **Separation of support and advice** — the system is a *decision-support* tool, not a regulated investment advisor (MiFID II disclaimer mandatory on all Fair Value / Value Score screens).
5. **Cache-first for external data, Yahoo Finance as runtime fallback** — FMP API calls are always backed by local DB/Redis; the system must function even if FMP is temporarily unavailable. When FMP cannot be reached (quota exceeded, outage, or API key absent), the `MarketDataClient` abstraction falls back to Yahoo Finance automatically — no API key required, same domain types returned. This fallback applies in every milestone, not only the M0 demo.
6. **Immutable historical data** — once a fundamental snapshot is ingested, it is never overwritten; corrections append new records.
7. **Secrets never in source control** — API keys, passwords, and tokens live exclusively in `.env` (local) or injected environment variables (CI/CD); no credential may appear in any committed file.
8. **Financial resilience before apparent cheapness** — evaluate leverage, liquidity, interest burden, cash generation, and dividend coverage over time. Show the underlying trend and sector context; never reduce financial health to a universal pass/fail ratio. Read leverage and coverage against sector-appropriate benchmarks, not one universal ratio — see Design Principle 16.
9. **Shared research universe for every user** — authenticated `INVESTOR`, `ADVISOR`, and `ADMIN` users can seed custom ticker lists into the platform-wide security universe for research. Admins can additionally manage named packs and broader shared-universe maintenance. Once a symbol or market pack is seeded, all authenticated users can discover it through search, screener, watchlist, portfolio, and security-detail flows according to their role and ownership rules.
10. **Research starts with business understanding** — market-wide search and screener results must include enough company context to decide whether a stock deserves deeper analysis: symbol, company name, sector, exchange, country when available, and a concise business description or profile excerpt.
11. **Single-stock analysis must be complete enough to act as a research packet** — the application must provide a dedicated in-depth review page for each seeded stock. It must expose DCF, free cash flow, Graham number, margin of safety, earnings, debt, dividend sustainability, dividend yield, liquidity including quick ratio when available, source coverage, freshness, and clear data-availability labels when a provider cannot supply a metric. For REIT-classified securities, this packet must also expose FFO, AFFO, and Debt/EBITDA per Design Principle 16 — a GAAP-earnings-only packet is not complete for this sector.
12. **Missing data must be explainable** — users must be able to distinguish provider limitations, stale provider data, missing seeded history, missing internal computation, and valuation guardrail failures. A blank score or metric is never enough on its own.
13. **Portfolio exposure must be visible before action** — portfolio tools must surface concentration by holding and sector when data is available. The platform explains exposure and risk, while preserving the decision-support boundary and avoiding order recommendations.
14. **Research rationale belongs with the workflow** — watchlists, comparison views, and narrative-check workflows should let users record why a symbol is being monitored, what signal would change the view, and what data gaps remain.
15. **AI-assisted thesis synthesis is interpretation, not computation** — where an AI investment-thesis agent is available (Vertex AI / Gemini, see `specs/roadmap.md` → Group TA), it may only interpret and narrate VIS-computed financial context — bull case, bear case, risks, invalidation conditions — with every claim traceable to a supplied input field. It must never compute DCF, Graham Number, DDM, Margin of Safety, or Value Score itself; never retrieve external data or reason from knowledge outside the supplied context; never issue `BUY`/`SELL`/`HOLD` instructions; and must flag insufficient, contradictory, or stale data for human review rather than guess. The MiFID II decision-support disclaimer (Principle 4) applies to AI-generated thesis text exactly as it applies to every other Fair Value/Value Score output. This capability supersedes the locally fine-tuned Gemma/QLoRA path piloted in `vis-model-training/`, which was closed after failing its output-quality gate.
16. **Sector-appropriate valuation metrics** — GAAP-earnings multiples (P/E, ROE/ROIC, Debt/Equity, EPS payout ratio) are structurally distorted for capital-intensive, non-cash-charge-heavy sectors: real-estate depreciation suppresses net income and inflates P/E while understating quality, and REITs are structurally leveraged by business model (≥90% of taxable income must be distributed, so equity cannot build the way it does at an industrial company), making a universal Debt/Equity band punitive rather than informative. Where a sector-recognized alternative standard exists — REITs' FFO/AFFO per NAREIT is the first case — the platform computes and surfaces that standard instead of forcing GAAP metrics onto a business model they don't fit, and screener/security-detail views for that sector carry an explicit caveat on the GAAP metrics that remain visible, until the sector-aware replacement is available. This is additive: sectors without a defined alternative keep today's metrics unchanged, and this principle never relaxes Principle 2 (transparency) or Principle 12 (missing data must be explainable) — an estimated figure (e.g. cap-rate-derived NAV) must always show its formula and assumptions, never be presented as a precise appraisal. Governed by the `SectorMetricProfile` classification defined in `specs/sector-aware-valuation-metrics.md` and implemented in `specs/roadmap.md` → Group RM.

## Cloud Distribution Path

The platform will move to GCP in three deliberately progressive phases. Each phase preserves the decision-support boundary, immutable historical records, cache-first market-data behaviour, and safe handling of secrets.

| Phase | Goal | Deployment boundary |
|---|---|---|
| **K1 - Stakeholder Cloud Deployment** | Make the working application safely accessible for internal/stakeholder evaluation. | One containerised Spring Boot service on Cloud Run, managed PostgreSQL/Redis, injected secrets, and basic health/log visibility. |
| **K2 - Production-Shaped GCP Platform** | Make the MVP repeatable, scalable, and operationally safe. | Terraform-managed environments; Cloud Run API separated from Cloud Run Jobs; Cloud Scheduler, private managed data services, CI/CD, backups, monitoring, and a custom HTTPS domain. |
| **K3 - Commercial & Compliance Hardening** | Prepare a customer-facing service for financial-data, privacy, and continuity obligations. | Security controls and operational evidence, restore drills, least-privilege access, regional/data-residency decisions, incident runbooks, and confirmed FMP/GDPR/MiFID II obligations. |

> The AI Investment Thesis capability (Vertex AI / Gemini, Principle 15, `specs/roadmap.md` → Group TA) is a managed Google Cloud API reachable independently of this deployment path — it does not require K1–K3 to be complete, and K1–K3 do not require it.

## Out of Scope (MVP v1)

- Order execution / brokerage integration
- Derivatives and options
- Alternative assets (private equity, real estate, crypto)
- Portfolio accounting / P&L tracking
- Custodian bank integration
- Real-time streaming market data
