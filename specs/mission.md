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
5. **Cache-first for external data** — FMP API calls are always backed by local DB/Redis; the system must function even if FMP is temporarily unavailable.
6. **Immutable historical data** — once a fundamental snapshot is ingested, it is never overwritten; corrections append new records.

## Out of Scope (MVP v1)

- Order execution / brokerage integration
- Derivatives and options
- Alternative assets (private equity, real estate, crypto)
- Portfolio accounting / P&L tracking
- Custodian bank integration
- Real-time streaming market data
