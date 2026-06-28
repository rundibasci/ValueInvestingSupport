# Persona 2 - Hedge-Fund Asset Allocator

## Persona Assumptions

- Professional allocator focused on quality, margin durability, scalability, and concentration risk.
- Will tolerate fewer positions only when valuation and data quality justify concentration.
- Treats model portfolio output as workflow evidence, not investment advice.

## Source Summaries Used

- MSFT: high-quality cloud and software compounder.
- NVDA: AI infrastructure leader with high expectations and valuation risk.
- ADBE: high-margin software franchise with growth-quality questions.

## Candidate Selection And Seeding

Seed list:

```text
MSFT, NVDA, ADBE
```

Seed result after the HD3 transaction fix:

| Symbol | Company | MoS | Score | Recommendation | Error |
|---|---|---:|---:|---|---|
| MSFT | Microsoft Corporation | -189.42% | 69.00 | OVERVALUED | none |
| NVDA | NVIDIA Corporation | -459.19% | unavailable | OVERVALUED | none |
| ADBE | Adobe Inc. | -90.27% | unavailable | OVERVALUED | none |

## Research Packet Evidence

| Symbol | Price | MoS | Score | Annual Rows | Ratio Rows | Data Notes |
|---|---:|---:|---:|---:|---:|---:|
| MSFT | 420.00 | -189.42% | 69.00 | 10 | 10 | 5 |
| NVDA | 192.53 | -459.19% | unavailable | 4 | 10 | 6 |
| KO | 60.00 | -153.70% | 74.50 | 10 | 10 | 5 |

## Portfolio Output

Portfolio: `HD3 Allocator Quality Portfolio`

| Holding | Quantity | Current Value | Weight | MoS | Recommendation |
|---|---:|---:|---:|---:|---|
| MSFT | 5 | 2100.00 | 81.40% | -189.42% | OVERVALUED |
| KO | 8 | 480.00 | 18.60% | -153.70% | OVERVALUED |

Weighted MoS: `-182.78%`

This intentionally created a concentration-heavy portfolio to test whether the app makes concentration visible. The resulting 81.40% MSFT weight is a useful beta finding: the platform displays the weight, but does not strongly warn on concentration in the portfolio detail workflow.

## Watchlist Output

| Symbol | MoS Alert Min | Rationale |
|---|---:|---|
| NVDA | 10.00% | Track quality/growth story until valuation and data completeness improve. |
| ADBE | 10.00% | Monitor software quality candidate with valuation risk. |

## Platform Impressions

- The workflow supports allocator-style comparison, but the screener returned no results under strict quality filters.
- Portfolio concentration is calculable but needs clearer risk signaling.
- Newly seeded high-growth symbols can lack scores, which weakens professional comparison.
- The review page is useful for source/data caveats, but the allocator needs cross-symbol comparison faster than opening reviews one by one.

## Recommendations

- Blocker: none after the seed transaction fix.
- Product gap: portfolio concentration warnings should be visible before or immediately after adding holdings.
- Product gap: score computation should be triggerable or clearly queued for newly seeded symbols.
- UX polish: screener empty states should explain which filters eliminated all candidates.
- Data-quality concern: newly seeded NVDA/ADBE had valuation and ratios but no value score.
- Nice-to-have: comparison table for selected symbols with MoS, score, ROIC, debt, growth, and data completeness.
