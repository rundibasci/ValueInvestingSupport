package it.mazzoni.vis.moat;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class CapitalAllocationService {
    private final SecurityRepository securityRepository;
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;
    private final CapitalAllocationResultRepository capitalAllocationResultRepository;

    public CapitalAllocationService(SecurityRepository securityRepository,
                                    FundamentalSnapshotRepository fundamentalSnapshotRepository,
                                    RatioSnapshotRepository ratioSnapshotRepository,
                                    CapitalAllocationResultRepository capitalAllocationResultRepository) {
        this.securityRepository = securityRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.capitalAllocationResultRepository = capitalAllocationResultRepository;
    }

    @Transactional
    public CapitalAllocationResult analyze(String symbol) {
        Security security = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));
        return analyze(security);
    }

    @Transactional
    public CapitalAllocationResult analyze(Security security) {
        List<FundamentalSnapshot> annuals = fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL)
                .stream().filter(f -> f.getSharesOutstanding() != null && f.getSharesOutstanding() > 0).limit(10).toList();
        RatioSnapshot latestRatio = ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(security).orElse(null);

        CapitalAllocationResult result = new CapitalAllocationResult();
        result.setSecurity(security);
        result.setResultDate(LocalDate.now());
        result.setYearsAnalyzed(annuals.size());
        result.setDividendYield(latestRatio != null ? MoatMath.normalizeRatio(latestRatio.getDividendYield()) : null);
        result.setInsiderOwnershipPercentage(null);
        result.setAcquisitionSpendToFcf(null);

        if (annuals.size() < 2) {
            result.setSharesOutstandingTrend(SharesOutstandingTrend.INSUFFICIENT_DATA);
            result.setClassification(CapitalAllocatorClassification.INSUFFICIENT_DATA);
            result.setAvailabilityMessage("At least two annual shares outstanding observations are required.");
        } else {
            BigDecimal latest = BigDecimal.valueOf(annuals.get(0).getSharesOutstanding());
            BigDecimal oldest = BigDecimal.valueOf(annuals.get(annuals.size() - 1).getSharesOutstanding());
            int years = Math.max(1, annuals.size() - 1);
            BigDecimal change = MoatMath.percentChange(latest, oldest);
            BigDecimal cagr = MoatMath.cagr(latest, oldest, years);
            SharesOutstandingTrend trend = classifySharesTrend(cagr);
            BigDecimal netBuybackYield = cagr != null ? cagr.negate() : null;
            BigDecimal totalShareholderYield = result.getDividendYield() != null && netBuybackYield != null
                    ? result.getDividendYield().add(netBuybackYield) : null;
            result.setSharesChangePercentage(change);
            result.setSharesCagr(cagr);
            result.setSharesOutstandingTrend(trend);
            result.setNetBuybackYield(netBuybackYield);
            result.setTotalShareholderYield(totalShareholderYield);
            result.setClassification(classifyAllocator(trend, result.getDividendYield()));
            result.setAvailabilityMessage("Insider ownership percentage and acquisition spend are unavailable in the current persisted provider model.");
        }

        capitalAllocationResultRepository.deleteBySecurity(security);
        return capitalAllocationResultRepository.save(result);
    }

    private SharesOutstandingTrend classifySharesTrend(BigDecimal cagr) {
        if (cagr == null) return SharesOutstandingTrend.INSUFFICIENT_DATA;
        if (cagr.compareTo(new BigDecimal("0.02")) > 0) return SharesOutstandingTrend.NET_DILUTER;
        if (cagr.compareTo(new BigDecimal("-0.01")) < 0) return SharesOutstandingTrend.NET_BUYBACK;
        return SharesOutstandingTrend.STABLE;
    }

    private CapitalAllocatorClassification classifyAllocator(SharesOutstandingTrend trend, BigDecimal dividendYield) {
        if (trend == SharesOutstandingTrend.INSUFFICIENT_DATA) return CapitalAllocatorClassification.INSUFFICIENT_DATA;
        if (trend == SharesOutstandingTrend.NET_DILUTER) return CapitalAllocatorClassification.NET_DILUTER;
        if ((trend == SharesOutstandingTrend.NET_BUYBACK || trend == SharesOutstandingTrend.STABLE)
                && dividendYield != null && dividendYield.compareTo(BigDecimal.ZERO) > 0) {
            return CapitalAllocatorClassification.DISCIPLINED_CAPITAL_ALLOCATOR;
        }
        return CapitalAllocatorClassification.STABLE;
    }
}
