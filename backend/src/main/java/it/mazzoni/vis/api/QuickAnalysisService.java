package it.mazzoni.vis.api;

import it.mazzoni.vis.api.dto.QuickAnalysisResponse;
import it.mazzoni.vis.config.ValuationDefaultsProperties;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.valuation.StaleDataException;
import it.mazzoni.vis.valuation.ValuationDataUnavailableException;
import it.mazzoni.vis.valuation.ValuationOutcome;
import it.mazzoni.vis.valuation.ValuationParams;
import it.mazzoni.vis.valuation.ValuationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class QuickAnalysisService {

    private static final int STALE_DAYS = 7;

    private final SecurityRepository securityRepository;
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
    private final ValuationService valuationService;
    private final ValuationDefaultsProperties defaults;

    public QuickAnalysisService(
            SecurityRepository securityRepository,
            FundamentalSnapshotRepository fundamentalSnapshotRepository,
            ValuationService valuationService,
            ValuationDefaultsProperties defaults) {
        this.securityRepository = securityRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.valuationService = valuationService;
        this.defaults = defaults;
    }

    public QuickAnalysisResponse analyze(String symbol) {
        Security security = securityRepository.findBySymbol(symbol)
                .orElseThrow(() -> new SymbolNotFoundException(symbol));

        FundamentalSnapshot snapshot = loadSnapshot(security, symbol);
        checkStale(snapshot, symbol);

        ValuationParams params = new ValuationParams(
                defaults.wacc(), defaults.growthY1Y5(), defaults.growthY6Y10(),
                defaults.terminalRate(), null, null);

        ValuationOutcome outcome = valuationService.calculate(symbol, params);
        ValuationResult result = outcome.result();

        return buildResponse(security, snapshot, result);
    }

    private FundamentalSnapshot loadSnapshot(Security security, String symbol) {
        Optional<FundamentalSnapshot> ttm = fundamentalSnapshotRepository
                .findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM);
        if (ttm.isPresent()) {
            return ttm.get();
        }
        return fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ValuationDataUnavailableException(symbol));
    }

    private void checkStale(FundamentalSnapshot snapshot, String symbol) {
        if (snapshot.getReportDate() != null
                && snapshot.getReportDate().isBefore(LocalDate.now().minusDays(STALE_DAYS))) {
            throw new StaleDataException(symbol, snapshot.getReportDate());
        }
    }

    private QuickAnalysisResponse buildResponse(Security security, FundamentalSnapshot snapshot,
                                                 ValuationResult result) {
        QuickAnalysisResponse.DcfRange dcfRange = result.getDcfFairValue() != null
                ? new QuickAnalysisResponse.DcfRange(
                        result.getDcfFairValue(),
                        result.getDcfFairValueLow(),
                        result.getDcfFairValueHigh())
                : null;

        QuickAnalysisResponse.ValuationSummary valuation = new QuickAnalysisResponse.ValuationSummary(
                dcfRange, result.getGrahamNumber(), result.getCompositeFairValue());

        QuickAnalysisResponse.FinancialSummary financialSummary = new QuickAnalysisResponse.FinancialSummary(
                snapshot.getRevenue(), snapshot.getNetIncome(),
                snapshot.getFreeCashFlow(), snapshot.getEpsDiluted());

        return new QuickAnalysisResponse(
                security.getSymbol(),
                security.getCompanyName(),
                result.getCurrentPrice(),
                security.getCurrency(),
                security.getSector(),
                financialSummary,
                valuation,
                result.getMarginOfSafety(),
                result.getRecommendation(),
                QuickAnalysisResponse.DISCLAIMER,
                snapshot.getReportDate(),
                "fmp"
        );
    }
}
