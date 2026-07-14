package it.mazzoni.vis.security;

import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.security.dto.PeerItem;
import it.mazzoni.vis.security.dto.PeersResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
public class PeersController {

    private final SecurityRepository securityRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;
    private final ValuationResultRepository valuationResultRepository;
    private final ValueScoreRepository valueScoreRepository;

    public PeersController(SecurityRepository securityRepository,
                           RatioSnapshotRepository ratioSnapshotRepository,
                           ValuationResultRepository valuationResultRepository,
                           ValueScoreRepository valueScoreRepository) {
        this.securityRepository = securityRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.valuationResultRepository = valuationResultRepository;
        this.valueScoreRepository = valueScoreRepository;
    }

    @GetMapping("/{symbol}/peers")
    public ResponseEntity<PeersResponse> peers(@PathVariable String symbol) {
        Security subject = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));

        if (subject.getSector() == null) {
            return ResponseEntity.ok(new PeersResponse(symbol.toUpperCase(), List.of()));
        }

        BigDecimal subjectCap = subject.getMarketCap() != null ? subject.getMarketCap() : BigDecimal.ZERO;

        List<PeerItem> peers = securityRepository
                .findByActiveTrueAndSectorAndSymbolNot(subject.getSector(), subject.getSymbol())
                .stream()
                .sorted(Comparator.comparing(p -> {
                    BigDecimal cap = p.getMarketCap() != null ? p.getMarketCap() : BigDecimal.ZERO;
                    return cap.subtract(subjectCap).abs();
                }))
                .limit(5)
                .map(peer -> buildPeerItem(peer))
                .toList();

        return ResponseEntity.ok(new PeersResponse(symbol.toUpperCase(), peers));
    }

    private PeerItem buildPeerItem(Security peer) {
        ValuationResult valuation = valuationResultRepository
                .findTopBySecurityOrderByValuationDateDesc(peer)
                .orElse(null);
        ValueScore score = valueScoreRepository
                .findTopBySecurityOrderByScoreDateDesc(peer)
                .orElse(null);
        RatioSnapshot ratios = ratioSnapshotRepository
                .findTopBySecurityOrderByReportDateDesc(peer)
                .orElse(null);

        return new PeerItem(
                peer.getSymbol(),
                peer.getCompanyName(),
                valuation != null ? valuation.getCurrentPrice() : null,
                valuation != null ? valuation.getCompositeFairValue() : null,
                valuation != null ? valuation.getMarginOfSafety() : null,
                score != null ? score.getTotalScore() : null,
                ratios != null ? ratios.getPeRatio() : null,
                ratios != null ? ratios.getRoic() : null
        );
    }
}
