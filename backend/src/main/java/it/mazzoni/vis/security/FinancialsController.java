package it.mazzoni.vis.security;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.security.dto.AnnualFinancials;
import it.mazzoni.vis.security.dto.FinancialsResponse;
import it.mazzoni.vis.security.dto.QuarterlyFinancials;
import it.mazzoni.vis.security.dto.TtmFinancials;
import it.mazzoni.vis.valuation.ValuationDataUnavailableException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
public class FinancialsController {

    private final SecurityRepository securityRepository;
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;

    public FinancialsController(SecurityRepository securityRepository,
                                FundamentalSnapshotRepository fundamentalSnapshotRepository) {
        this.securityRepository = securityRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
    }

    @GetMapping("/{symbol}/financials")
    public ResponseEntity<FinancialsResponse> financials(@PathVariable String symbol) {
        Security security = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));

        List<FundamentalSnapshot> annualSnapshots = fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL);

        if (annualSnapshots.isEmpty()) {
            throw new ValuationDataUnavailableException(symbol);
        }

        List<AnnualFinancials> annuals = annualSnapshots.stream()
                .collect(LinkedHashMap<Integer, FundamentalSnapshot>::new,
                        (byYear, snapshot) -> byYear.putIfAbsent(snapshot.getFiscalYear(), snapshot),
                        LinkedHashMap::putAll)
                .values()
                .stream()
                .limit(10)
                .map(AnnualFinancials::from)
                .toList();

        List<QuarterlyFinancials> quarters = fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.QUARTERLY)
                .stream()
                .limit(8)
                .map(QuarterlyFinancials::from)
                .toList();

        TtmFinancials ttm = fundamentalSnapshotRepository
                .findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM)
                .map(TtmFinancials::from)
                .orElse(null);

        return ResponseEntity.ok(new FinancialsResponse(symbol.toUpperCase(), annuals, quarters, ttm));
    }
}
