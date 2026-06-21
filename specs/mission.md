# Mission — Value Investing Advisory Platform

## Purpose

Build a software platform that guides financial advisors and self-directed investors through the full Value Investing cycle: from discovering undervalued stocks to constructing and monitoring a model portfolio.

## Core Value Proposition

> Given a universe of thousands of publicly traded companies, surface the handful that are fundamentally sound, competitively advantaged, and priced below their intrinsic value — then help the user build and manage a portfolio of them.

## Value Investing Cycle (the system's spine)

```
Screening → Fundamental Analysis → Intrinsic Value Estimation
    → Margin of Safety Calculation → Recommendation
        → Portfolio Construction → Continuous Monitoring
```

Every feature must map to one or more steps in this cycle.

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
8. **Financial resilience before apparent cheapness** — evaluate leverage, liquidity, interest burden, cash generation, and dividend coverage over time. Show the underlying trend and sector context; never reduce financial health to a universal pass/fail ratio.

## Cloud Distribution Path

The platform will move to GCP in three deliberately progressive phases. Each phase preserves the decision-support boundary, immutable historical records, cache-first market-data behaviour, and safe handling of secrets.

| Phase | Goal | Deployment boundary |
|---|---|---|
| **K1 - Stakeholder Cloud Deployment** | Make the working application safely accessible for internal/stakeholder evaluation. | One containerised Spring Boot service on Cloud Run, managed PostgreSQL/Redis, injected secrets, and basic health/log visibility. |
| **K2 - Production-Shaped GCP Platform** | Make the MVP repeatable, scalable, and operationally safe. | Terraform-managed environments; Cloud Run API separated from Cloud Run Jobs; Cloud Scheduler, private managed data services, CI/CD, backups, monitoring, and a custom HTTPS domain. |
| **K3 - Commercial & Compliance Hardening** | Prepare a customer-facing service for financial-data, privacy, and continuity obligations. | Security controls and operational evidence, restore drills, least-privilege access, regional/data-residency decisions, incident runbooks, and confirmed FMP/GDPR/MiFID II obligations. |

## Out of Scope (MVP v1)

- Order execution / brokerage integration
- Derivatives and options
- Alternative assets (private equity, real estate, crypto)
- Portfolio accounting / P&L tracking
- Custodian bank integration
- Real-time streaming market data
