# Value Investor Roadmap Critical Review

**Date:** 2026-06-28
**Reviewer perspective:** Experienced value investor (20+ years), practitioner of Graham/Buffett/Munger methodology, portfolio manager for own capital and advisory clients.

**Purpose:** Identify gaps, risks, questionable assumptions, and missing fundamentals in the current roadmap from the perspective of someone who would actually use this platform to make investment decisions.

---

## 1. VALUATION MODEL CONCERNS

### 1.1 DCF Weight Too High in Composite (60%)

The composite fair value weighs DCF at 60%, Graham at 25%, DDM at 15%. DCF is the most assumption-sensitive model — small changes in WACC, terminal growth, or projected growth rates can swing fair value by 30–50%. Putting 60% weight on the model most vulnerable to garbage-in-garbage-out is dangerous for a platform targeting conservative investors.

Graham himself warned against forecasting. The platform should at minimum:
- Show how sensitive the composite is to DCF input changes (sensitivity table)
- Allow users to configure composite weights per their own conviction
- Display the DCF fair value range spread as a confidence indicator — if low and high differ by more than 40%, the DCF is unreliable and its composite weight should decrease automatically

**Severity:** High — this directly affects the core output users rely on.

### 1.2 WACC Calculation Not Specified

The DCF engine accepts WACC as an input (`DcfInput.wacc`), but nowhere in the roadmap is WACC calculation defined. Where does beta come from? Which risk-free rate (10Y treasury? Which country?)? What equity risk premium? What debt cost assumption?

A naive WACC — or worse, a user-entered WACC with no guidance — can make any stock look cheap or expensive. If the platform provides a "custom DCF" feature, it must also provide a defensible default WACC with transparent assumptions.

**Severity:** High — WACC is the single most impactful DCF parameter.

### 1.3 No Earnings Power Value (EPV)

Bruce Greenwald's Earnings Power Value is arguably the most conservative valuation method: it values the business on current normalized earnings power with zero growth. For a platform with "conservative defaults" as a design principle, the absence of EPV is a significant gap. EPV provides a floor valuation that complements DCF's ceiling-oriented approach.

**Severity:** Medium — adds analytical depth for conservative investors.

### 1.4 No Owner Earnings Concept

Buffett's "owner earnings" (net income + depreciation/amortization − average maintenance capex) is distinct from FCF and provides a different view of true economic earnings. The platform uses FCF throughout but never mentions owner earnings. For a value investing platform, this is a notable omission.

**Severity:** Low-Medium — FCF is a reasonable proxy, but sophisticated users expect owner earnings.

### 1.5 Terminal Value as Percentage of DCF Not Visible

DCF terminal value typically accounts for 60–80% of total present value. If the terminal value dominates, the DCF is really just a disguised perpetuity model, and the 5-year projections are irrelevant noise. The platform should display terminal value as a percentage of total DCF and flag when it exceeds 70% — that's a signal that the valuation depends almost entirely on long-term assumptions.

**Severity:** Medium — transparency issue for trust in DCF results.

### 1.6 Graham Number Is Not Graham's Method

`GrahamCalculator.calculate(eps, bvps) → BigDecimal` implements the Graham Number formula (√(22.5 × EPS × BVPS)), but Graham's actual method was a multi-factor screen:
- P/E < 15 (or P/E × P/B < 22.5)
- Current ratio > 2.0
- No negative earnings in the last 5 years
- 10-year earnings stability
- Dividend record of at least 20 years
- Moderate debt

Reducing Graham to a single number misrepresents the methodology. The platform should offer a "Graham Criteria Checklist" showing how many of Graham's original requirements each stock passes, not just the number.

**Severity:** Medium — misrepresents a well-known methodology that users will compare against.

---

## 2. SCORING FORMULA CONCERNS

### 2.1 Fixed Weights Penalize Legitimate Business Types

MoS 30, Quality 25, Safety 20, Growth 15, Dividend 10 = 100. Problems:
- **Non-dividend payers** (BRK.B, GOOG, AMZN) have a max score of 90. This penalizes some of the best businesses in history. A quality growth company with no dividend should not be structurally disadvantaged.
- **REITs, utilities, MLPs** have different financial structures. Debt-to-equity norms differ by sector. Applying the same Safety sub-score formula to a REIT and a tech company produces misleading comparisons.
- **A company with strong Quality + Growth + Dividend but negative MoS (overvalued)** can still score 55+/100. That's dangerous — a value investor should never see a high score on an overvalued stock. MoS should be a gate, not just a weight.

**Severity:** High — scoring formula affects every screening and ranking decision.

### 2.2 No Piotroski F-Score

FMP provides Piotroski data (mentioned in tech-stack: "Piotroski"). The F-Score is a widely used value investing metric for separating high book-to-market winners from losers. Its absence from both the scoring formula and the screener filters is surprising given the data is available.

**Severity:** Medium — easy win, data already available.

### 2.3 No Altman Z-Score or Bankruptcy Risk

Value investing means buying cheap stocks. Some cheap stocks are cheap for good reason — they're heading toward distress. Without an Altman Z-Score or equivalent bankruptcy risk indicator, the platform can't distinguish genuine value from value traps in distressed situations.

**Severity:** Medium — critical for safety-conscious investors.

### 2.4 No Cyclicality Awareness in Scoring

Cyclical companies (industrials, materials, energy, autos) look cheap at cycle peaks (low P/E on peak earnings) and expensive at troughs (high P/E on depressed earnings). The scoring and valuation engines have no concept of cycle position. Normalized earnings (average over a full business cycle, typically 7–10 years) are needed for cyclicals but are never mentioned.

**Severity:** High — value traps in cyclical stocks are one of the most common mistakes.

---

## 3. DATA QUALITY & TRUST

### 3.1 Single Data Provider, No Cross-Verification

The entire platform depends on FMP for production data. A misstated EPS, wrong balance sheet number, or delayed filing update flows unchecked into DCF, Graham, Score, and Recommendation. Professional value investors always cross-reference key figures against at least one independent source (10-K/10-Q filings, SEC EDGAR, company investor relations).

The platform should at minimum:
- Flag when fundamental data is older than 90 days from the expected filing date
- Consider a secondary data source for spot-checking critical inputs (EPS, book value, FCF, shares outstanding)
- Display the filing date of the underlying data, not just "dataAsOf"

**Severity:** High — a single wrong EPS or share count silently corrupts every downstream calculation.

### 3.2 Yahoo Finance Demos May Erode Trust

RD1 and RD2 build stakeholder-facing demos on Yahoo Finance, which the tech-stack explicitly warns is unofficial with no SLA. If a demo fails because Yahoo changed an endpoint or rate-limited the request, that's a credibility hit. The data may also differ from FMP for the same company, confusing anyone who compares demo results to production results later.

**Severity:** Medium — operational risk for stakeholder demos.

### 3.3 No Data Reconciliation Between Sources

When Yahoo fallback activates (FMP quota exceeded, outage), the platform switches silently. But Yahoo and FMP may report different figures for the same metric on the same company. There's no reconciliation, no warning to the user that today's valuation used a different data source than yesterday's, and no flagging of discrepancies that exceed a tolerance threshold.

**Severity:** Medium — trust and consistency issue.

### 3.4 TTM vs. Fiscal Year Ambiguity

The roadmap mentions "ratios TTM bulk" for ingestion and "10y annual + 8 quarters + TTM" for display. But which period feeds the valuation engine? Rolling TTM can be distorted by seasonality. Fiscal-year-end figures are cleaner but potentially stale. The choice matters for every calculation, and it's not specified.

**Severity:** Medium — affects valuation accuracy.

---

## 4. ANALYTICAL GAPS

### 4.1 No Competitive Advantage / Moat Analysis

This is the biggest analytical gap. Buffett's #1 criterion is durable competitive advantage. The Quality sub-score exists (25% weight) but its definition references ROIC, ROE, and margins — these are outcomes, not moat identification. The platform has no way to assess:
- Return on capital consistency over 10+ years (the strongest moat signal)
- Revenue stability / customer concentration
- Capital intensity trends
- Reinvestment rate vs. return on incremental capital

At minimum, the review page should display ROIC over 10 years and flag whether it's consistently above cost of capital. That pattern IS the moat.

**Severity:** High — this is what separates value investing from cheap-stock buying.

### 4.2 No Management Quality Signals

Capital allocation track record is critical. The platform should surface:
- Shares outstanding trend (buybacks vs. dilution over 5–10 years)
- Total shareholder return vs. peers
- Insider ownership percentage (skin in the game)
- Acquisitions history (empire builders destroy value)

Insider transactions are tracked (E2) but buying/selling patterns are not analyzed for intent.

**Severity:** Medium — important for long-term conviction.

### 4.3 No Historical P/E Band or Valuation Context

Where does today's price sit relative to its own 5–10 year P/E, P/B, or EV/EBITDA range? A stock with a P/E of 12 might look cheap, but if it historically trades at P/E 8–10, it's actually at the high end. Historical valuation context is essential for calibrating whether today's MoS is unusual.

**Severity:** Medium — important context for MoS interpretation.

### 4.4 No Earnings Quality / Accruals Analysis

A company can show rising EPS driven by accrual accounting choices rather than cash generation. The Sloan accruals ratio (or simpler: FCF/Net Income ratio) is a well-known earnings quality indicator. If this ratio is consistently below 1.0, earnings quality is poor. The platform tracks both FCF and net income but never computes or displays this ratio.

**Severity:** Medium — common value trap indicator.

### 4.5 No Revenue/Earnings Segment Breakdown

Understanding geographic and business segment revenue composition is essential for assessing concentration risk, growth sources, and regulatory exposure. The platform shows aggregate numbers only.

**Severity:** Low-Medium — depends on data availability from providers.

---

## 5. PORTFOLIO CONSTRUCTION CONCERNS

### 5.1 No Minimum Position Size Constraint

The 25% max per stock is specified, but no minimum. A 0.5% position in a high-conviction stock is a waste of portfolio real estate. Concentrated value investors typically hold 8–15 stocks with meaningful weights. The simulator should warn about positions below a meaningful threshold (e.g., 3%).

**Severity:** Low — but relevant for serious portfolio construction.

### 5.2 No Liquidity Constraint

Small-cap value stocks can have thin trading volumes. A 10% portfolio position in an illiquid stock creates execution risk — you can't enter or exit without moving the price. The portfolio simulator has no concept of liquidity or average daily volume relative to position size.

**Severity:** Medium — practical concern for real portfolio execution.

### 5.3 No Transaction Cost or Tax Awareness

Rebalancing recommendations suggest buys/sells without considering:
- Transaction costs (particularly for smaller accounts)
- Tax implications of selling (short-term vs. long-term capital gains)
- Wash sale rules
- The value investor's preference for low turnover

Rebalance suggestions should include estimated tax impact and distinguish "must rebalance" (concentration breach) from "could rebalance" (drift within tolerance).

**Severity:** Medium — directly affects real-world returns.

### 5.4 No Benchmark or Performance Context

No mention of comparing the portfolio's characteristics (weighted P/E, MoS, yield, quality) against a benchmark index. Without this, the user can't evaluate whether the platform's screening and scoring actually produce portfolios that look different from (and presumably better than) the market.

**Severity:** Medium — essential for demonstrating platform value.

### 5.5 No Portfolio-Level Risk Metrics

Individual stock analysis is thorough, but portfolio-level risk is invisible:
- Weighted average MoS (is the portfolio collectively undervalued?)
- Sector concentration heat map
- Correlation between holdings
- Scenario analysis (what if interest rates rise 200bps? Recession?)

**Severity:** Medium — portfolio construction without portfolio-level metrics is incomplete.

---

## 6. WORKFLOW & PRIORITY CONCERNS

### 6.1 Too Many Demo/Assessment Phases

HD1, HD2, HD3, HD4, RD1-1, RD1-2, RD2-1 — seven demo and assessment phases. Each produces reports, screenshots, and documentation. This is valuable for process maturity but diverts engineering effort from analytical depth. The platform's analytical capabilities (valuation models, scoring, risk assessment) would benefit more from that effort.

Consider consolidating: RD1 + RD2 could be one phase with two steps. HD3 + HD4 could be tighter.

**Severity:** Medium — prioritization concern, not a technical gap.

### 6.2 Conservative Research (Group L) Comes Last

Group L (Conservative Workflow Hardening) is positioned after Groups K (GCP) and after both demo phases (RD1, RD2). But conservative research features — comparison views, availability diagnostics, research notes, concentration evidence — are core product functionality, not hardening. They should be available before stakeholder demos, not after cloud deployment.

Consider moving L1–L4 before K, or at least before RD2.

**Severity:** Medium — sequencing affects stakeholder perception of product completeness.

### 6.3 Universe Curation Templates Will Go Stale

SC1 defines templates like "dividend-aristocrats" with static criteria. But the actual dividend aristocrats list changes annually (S&P publishes updates). The "value-candidates" template (P/E < 15, P/B < 1.5, positive FCF) is a screener preset, not a curated list. There's no mechanism to refresh template definitions or distinguish static lists from dynamic filter presets.

**Severity:** Low-Medium — maintenance concern.

---

## 7. REGULATORY & COMPLIANCE GAPS

### 7.1 ADVISOR Role Obligations Unaddressed

The ADVISOR role is defined as "Financial advisor managing client portfolios." If advisors use this platform for client portfolio decisions, they face regulatory obligations beyond MiFID II disclaimers:
- Suitability assessment (does this portfolio match the client's risk profile?)
- Best execution evidence
- Record-keeping of recommendation rationale with timestamps
- Conflict of interest disclosure

The platform has no client risk profile concept, no suitability checks, and no timestamped decision audit trail. Either the ADVISOR role needs to be scoped down (research-only, no client portfolio management) or these obligations need a roadmap group.

**Severity:** High — regulatory risk if the platform is used as described.

### 7.2 No Research Decision Audit Trail

When a user adds a stock to their portfolio, the platform doesn't capture:
- What the MoS, score, and valuation were at the time of the decision
- Which data source was active
- What the user's DCF assumptions were
- When alerts fired and how the user responded

For professional users, this audit trail is essential. Watchlist rationale notes are a start but they're user-written text, not system-captured decision state.

**Severity:** Medium-High — important for professional users and regulatory compliance.

---

## 8. MISSING VALUE INVESTING FUNDAMENTALS

### 8.1 No Investment Checklist Framework

Munger's checklist approach: before buying, verify a set of conditions. The platform's scoring formula is one interpretation, but sophisticated value investors have their own checklists. The platform should allow users to define custom checklists (e.g., "ROIC > 15% for 5+ years", "Debt/EBITDA < 3", "Dividend streak > 10 years") and see which stocks pass/fail each criterion.

This is different from screener filters — a checklist is applied to a specific stock during research, not to the universe for discovery.

**Severity:** Medium — differentiating feature for serious users.

### 8.2 No Circle of Competence Support

Value investing emphasizes staying within your "circle of competence" — industries and business models you understand well. The platform should let users mark sectors/industries they're comfortable with and optionally filter research to those areas. This is a simple but philosophically important feature.

**Severity:** Low — nice to have, philosophically aligned.

### 8.3 No Long-Term Stability Scoring (Graham Criteria)

Graham required: no negative earnings in the past 10 years, 10-year earnings growth, 20-year dividend record. These are explicit pass/fail criteria, not gradients. The platform should display these as checklist items on the review page, not bury them inside a composite score.

**Severity:** Medium — directly relevant to the platform's stated methodology.

### 8.4 No Intrinsic Value Confidence Level

Not all intrinsic value estimates are equally reliable. A stable consumer staples company with 20 years of consistent cash flows has a much more reliable DCF than a high-growth tech company with 3 years of positive FCF. The platform should compute and display a confidence level for its valuation estimates based on:
- Length and consistency of historical data
- Spread between DCF low/high scenarios
- Number of valuation models that could be applied (DCF + Graham + DDM vs. Graham only)
- Data source completeness

**Severity:** Medium — helps users calibrate trust in the output.

---

## 9. SUMMARY: PRIORITIZED RECOMMENDATIONS

### Must-address before production use:
1. **WACC calculation transparency** (1.2) — without this, DCF is a toy
2. **MoS as a gate, not just a weight** (2.1) — overvalued stocks should never score high
3. **Cyclicality awareness** (2.4) — otherwise the platform is a cyclical value-trap machine
4. **Single data provider verification** (3.1) — one wrong number corrupts everything downstream
5. **ADVISOR role regulatory scoping** (7.1) — legal risk

### Should-address before stakeholder confidence:
6. **DCF sensitivity / terminal value transparency** (1.1, 1.5) — trust in the core output
7. **Competitive advantage / moat signals** (4.1) — this is what value investing IS
8. **Historical valuation context** (4.3) — calibrates MoS interpretation
9. **Piotroski F-Score** (2.2) — data available, easy win
10. **Scoring formula flexibility by sector** (2.1) — prevents misleading cross-sector comparisons

### Should-address before professional adoption:
11. **Earnings quality / accruals** (4.4)
12. **Portfolio-level risk metrics** (5.5)
13. **Research decision audit trail** (7.2)
14. **Altman Z-Score** (2.3)
15. **Shares outstanding trend / dilution** (4.2)

### Nice-to-have for differentiation:
16. **Earnings Power Value** (1.3)
17. **Investment checklist framework** (8.1)
18. **Intrinsic value confidence level** (8.4)
19. **Benchmark comparison** (5.4)
20. **Circle of competence** (8.2)
