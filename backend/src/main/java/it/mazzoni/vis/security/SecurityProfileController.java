package it.mazzoni.vis.security;

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
import it.mazzoni.vis.security.dto.SecurityDetailResponse;
import it.mazzoni.vis.valuation.StaleDataException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
public class SecurityProfileController {

    private static final int STALE_DAYS = 7;

    private final SecurityRepository securityRepository;
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;
    private final PriceQuoteRepository priceQuoteRepository;

    public SecurityProfileController(SecurityRepository securityRepository,
                                     FundamentalSnapshotRepository fundamentalSnapshotRepository,
                                     RatioSnapshotRepository ratioSnapshotRepository,
                                     PriceQuoteRepository priceQuoteRepository) {
        this.securityRepository = securityRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.priceQuoteRepository = priceQuoteRepository;
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<SecurityDetailResponse> profile(@PathVariable String symbol) {
        Security security = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));

        FundamentalSnapshot snapshot = fundamentalSnapshotRepository
                .findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)
                .orElseThrow(() -> new SymbolNotFoundException(symbol));

        if (snapshot.getReportDate() != null &&
                snapshot.getReportDate().isBefore(LocalDate.now().minusDays(STALE_DAYS))) {
            throw new StaleDataException(symbol, snapshot.getReportDate());
        }

        RatioSnapshot ratios = ratioSnapshotRepository
                .findTopBySecurityOrderByReportDateDesc(security)
                .orElse(null);

        PriceQuote price = priceQuoteRepository
                .findTopBySecurityOrderByQuoteDateDesc(security)
                .orElse(null);

        return ResponseEntity.ok(SecurityDetailResponse.from(security, snapshot, ratios, price));
    }
}
