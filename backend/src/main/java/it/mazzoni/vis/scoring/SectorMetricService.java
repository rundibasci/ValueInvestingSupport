package it.mazzoni.vis.scoring;

import it.mazzoni.vis.common.SectorClassifier;
import it.mazzoni.vis.config.ValuationEnhancementProperties;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.valuation.OwnerEarningsCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Computes and persists RM2's REIT sector metrics (FFO, AFFO, P/FFO, P/AFFO, Net Debt/EBITDA,
 * EBITDA interest coverage, AFFO payout ratio) onto {@link RatioSnapshot} for REIT/real-estate
 * classified securities ({@link SectorClassifier#isReit}) — see
 * {@code specs/sector-aware-valuation-metrics.md} §2, §4, §7.
 *
 * <p>Every non-REIT security is untouched: {@link #compute(Security)} returns immediately without
 * reading or writing anything. This mirrors {@code MoatAssessmentService}/
 * {@code CapitalAllocationService}'s existing "seed/pipeline-time only, not nightly-scheduled"
 * shape — no {@code @Scheduled} caller exists for any of those services either, and
 * {@code ScoreService.getScore} recomputes {@code ValueScore} live on every read regardless, so
 * this class's staleness-between-seeds characteristic is not a new one this phase introduces.
 *
 * <p><b>Price derivation for historical (ANNUAL) rows.</b> This platform does not ingest a dense
 * historical daily-price archive ({@code QuoteRefreshJob} only refreshes current watchlist/holding
 * prices every 15 minutes), so there is no observed historical price to pair with each ANNUAL
 * {@link RatioSnapshot} row for a multi-year P/FFO band. Instead, the historical price is
 * <b>derived</b> from the row's own provider-supplied {@code peRatio} and the paired
 * {@link FundamentalSnapshot#getEps()}: {@code impliedPrice = peRatio × eps}. This is an implied
 * figure, not an observed market price — never confuse the two when reading this class. The
 * current (TTM) row instead uses the actual latest {@link PriceQuote#getClose()}.
 *
 * <p><b>AFFO's recurring-capex input</b> reuses {@link OwnerEarningsCalculator#estimateMaintenanceCapex}
 * unchanged — the same {@code valuation.enhancements.maintenance-capex-depreciation-ratio} (0.70)
 * config value already used by the DCF engine's owner-earnings calculation, applied to D&A rather
 * than to total capital expenditure, per {@code specs/sector-aware-valuation-metrics.md} §4.2's
 * explicit instruction to reuse that existing value rather than inventing a new split heuristic.
 *
 * <p><b>AFFO payout ratio's dividend-per-share input</b> is likewise derived, not newly ingested:
 * {@code dividendPerShare = payoutRatio × eps}, reusing the provider-supplied GAAP payout ratio
 * already on {@link RatioSnapshot} rather than adding a new dividends-paid ingestion field.
 *
 * <p><b>EBITDA interest coverage</b> uses {@code interestExpense}, confirmed present and populated
 * on FMP Premium's {@code /income-statement} for O/PLD/SPG during RM2's own live verification (not
 * assumed from RM1's earlier field list — see {@code specs/2026-09-02-rm2-sector-metric-profile/validation.md}).
 * Net Debt/EBITDA uses raw (unadjusted) {@code ebitda}, not a NAREIT-style EBITDAre that excludes
 * gains/losses on real-estate sales — RM1 confirmed that adjustment is not isolable from FMP's
 * fundamentals data, so this figure reads more conservatively (higher) than a REIT's own
 * company-reported adjusted leverage ratio; see validation.md for a concrete real-data comparison.
 */
@Service
@Transactional
public class SectorMetricService {

    private final SecurityRepository securityRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final ValuationEnhancementProperties valuationEnhancementProperties;
    private final OwnerEarningsCalculator ownerEarningsCalculator = new OwnerEarningsCalculator();

    public SectorMetricService(SecurityRepository securityRepository,
                                RatioSnapshotRepository ratioSnapshotRepository,
                                FundamentalSnapshotRepository fundamentalSnapshotRepository,
                                PriceQuoteRepository priceQuoteRepository,
                                ValuationEnhancementProperties valuationEnhancementProperties) {
        this.securityRepository = securityRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.valuationEnhancementProperties = valuationEnhancementProperties;
    }

    public void compute(String symbol) {
        Security security = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));
        compute(security);
    }

    public void compute(Security security) {
        if (!SectorClassifier.isReit(security.getSector())) {
            return;
        }

        List<RatioSnapshot> rows = ratioSnapshotRepository.findBySecurity(security);
        BigDecimal latestClose = priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)
                .map(PriceQuote::getClose)
                .orElse(null);

        for (RatioSnapshot row : rows) {
            Optional<FundamentalSnapshot> paired = fundamentalSnapshotRepository
                    .findBySecurityAndPeriodAndReportDate(security, row.getPeriod(), row.getReportDate());
            if (paired.isEmpty()) {
                continue;
            }
            enrich(row, paired.get(), latestClose);
            ratioSnapshotRepository.save(row);
        }
    }

    private void enrich(RatioSnapshot row, FundamentalSnapshot fundamental, BigDecimal latestClose) {
        BigDecimal netIncome = fundamental.getNetIncome();
        BigDecimal da = fundamental.getDepreciationAndAmortization();
        Long shares = fundamental.getSharesOutstanding();
        BigDecimal eps = fundamental.getEps();

        BigDecimal ffoPerShare = ffoPerShare(netIncome, da, shares);
        row.setFfoPerShare(ffoPerShare);

        BigDecimal affoPerShare = affoPerShare(ffoPerShare, da, shares);
        row.setAffoPerShare(affoPerShare);

        BigDecimal impliedPrice = row.getPeriod() == Period.TTM
                ? latestClose
                : impliedPrice(row.getPeRatio(), eps);
        row.setPriceToFfo(divide(impliedPrice, ffoPerShare));
        row.setPriceToAffo(divide(impliedPrice, affoPerShare));

        BigDecimal ebitda = fundamental.getEbitda();
        row.setNetDebtToEbitda(netDebtToEbitda(fundamental.getTotalDebt(), fundamental.getCash(), ebitda));
        row.setInterestCoverageEbitda(interestCoverageEbitda(ebitda, fundamental.getInterestExpense()));

        row.setAffoPayoutRatio(affoPayoutRatio(row.getPayoutRatio(), eps, affoPerShare));
    }

    private BigDecimal ffoPerShare(BigDecimal netIncome, BigDecimal depreciationAndAmortization, Long shares) {
        if (netIncome == null || depreciationAndAmortization == null || shares == null || shares <= 0) {
            return null;
        }
        BigDecimal ffo = netIncome.add(depreciationAndAmortization);
        return ffo.divide(BigDecimal.valueOf(shares), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal affoPerShare(BigDecimal ffoPerShare, BigDecimal depreciationAndAmortization, Long shares) {
        if (ffoPerShare == null || depreciationAndAmortization == null || shares == null || shares <= 0) {
            return null;
        }
        BigDecimal maintenanceCapex = ownerEarningsCalculator.estimateMaintenanceCapex(
                depreciationAndAmortization, valuationEnhancementProperties.maintenanceCapexDepreciationRatio());
        BigDecimal maintenanceCapexPerShare = maintenanceCapex.divide(BigDecimal.valueOf(shares), 4, RoundingMode.HALF_UP);
        return ffoPerShare.subtract(maintenanceCapexPerShare);
    }

    private BigDecimal impliedPrice(BigDecimal peRatio, BigDecimal eps) {
        if (peRatio == null || eps == null || peRatio.compareTo(BigDecimal.ZERO) <= 0 || eps.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return peRatio.multiply(eps);
    }

    private BigDecimal netDebtToEbitda(BigDecimal totalDebt, BigDecimal cash, BigDecimal ebitda) {
        if (totalDebt == null || cash == null || ebitda == null || ebitda.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return totalDebt.subtract(cash).divide(ebitda, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal interestCoverageEbitda(BigDecimal ebitda, BigDecimal interestExpense) {
        if (ebitda == null || interestExpense == null || interestExpense.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return ebitda.divide(interestExpense, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal affoPayoutRatio(BigDecimal payoutRatio, BigDecimal eps, BigDecimal affoPerShare) {
        if (payoutRatio == null || eps == null || affoPerShare == null || affoPerShare.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal dividendPerShare = payoutRatio.multiply(eps);
        return dividendPerShare.divide(affoPerShare, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }
}
