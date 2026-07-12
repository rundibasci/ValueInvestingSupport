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
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
public class SecurityProfileController {

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

        RatioSnapshot ratios = ratioSnapshotRepository
                .findTopBySecurityOrderByReportDateDesc(security)
                .orElse(null);

        PriceQuote price = priceQuoteRepository
                .findTopBySecurityOrderByQuoteDateDesc(security)
                .orElse(null);

        return ResponseEntity.ok(SecurityDetailResponse.from(security, snapshot, ratios, price));
    }
}
